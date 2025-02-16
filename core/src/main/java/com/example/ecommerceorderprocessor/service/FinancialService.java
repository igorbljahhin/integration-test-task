package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.financial.FinancialOrderRecord;
import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialService {

    private final AppConfig appConfig;

    private static Collection<FinancialOrderRecord> fromOrder(Order order) {
        return ofNullable(order.getOrderItems()).orElse(List.of()).stream()
                .map(orderItem -> FinancialOrderRecord.builder()
                        .currencyCode(order.getCurrencyCode())
                        .orderId(order.getOrderId())
                        .orderPaidAmount(order.getOrderPaid())
                        .orderTotal(order.getOrderTotal())
                        .productId(orderItem.getProductId())
                        .productName(orderItem.getProductName())
                        .productPrice(orderItem.getPrice())
                        .quantity(orderItem.getQuantity())
                        .build()
                )
                .collect(Collectors.toList());
    }

    private void ensureOutputDirectoryExistsAndValidate() {
        final Path directory = Paths.get(appConfig.getFinancial().getOutputDirectory());

        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create output directory: " + appConfig.getFinancial().getOutputDirectory(), e);
            }
        }

        // validate the path is a directory
        if (!Files.isDirectory(directory)) {
            throw new RuntimeException("Output directory is not a valid directory: " + appConfig.getFinancial().getOutputDirectory());
        }

        // validate the directory is writable
        if (!Files.isWritable(directory)) {
            throw new RuntimeException("Output directory is not writable: " + appConfig.getFinancial().getOutputDirectory());
        }
    }

    private String generateOutputFileName() {
        String fileNamePattern = appConfig.getFinancial().getFileNamePattern();

        if (fileNamePattern.contains("{datetime:")) {
            final String datetimePattern = StringUtils.substringBetween(fileNamePattern, "{datetime:", "}");
            final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern(datetimePattern));

            fileNamePattern = StringUtils.replace(fileNamePattern, "{datetime:" + datetimePattern + "}", timestamp);
        }

        return fileNamePattern;
    }

    @SneakyThrows
    public void writeOrderToFile(final Order order) {
        ensureOutputDirectoryExistsAndValidate();

        // find latest modified file in output directory
        final File[] files = new File(appConfig.getFinancial().getOutputDirectory()).listFiles();

        final File latestModifiedFile = Arrays.stream(files != null ? files : new File[0])
                .filter(File::isFile)
                .filter(file -> {
                    final String extension = StringUtils.substringAfterLast(appConfig.getFinancial().getFileNamePattern(), ".");

                    return StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(file.getName()), extension);
                })
                .filter(file -> {
                    final String outputFileNamePrefix = StringUtils.substringBefore(appConfig.getFinancial().getFileNamePattern(), "{");

                    return StringUtils.startsWithIgnoreCase(file.getName(), outputFileNamePrefix);
                })
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);

        String currentFileName;
        AtomicInteger currentRecordCount = new AtomicInteger(0);

        if (latestModifiedFile != null) {
            currentFileName = latestModifiedFile.getName();

            try {
                try (Stream<String> lines = Files.lines(Paths.get(latestModifiedFile.getAbsolutePath()))) {
                    currentRecordCount.set((int) lines.count() - 1 /* ignore header */);
                }

                log.info("Latest modified file in the financial output directory is {}, the number of lines is {}", latestModifiedFile, currentRecordCount.get());
            } catch (IOException e) {
                currentFileName = generateOutputFileName();

                log.error("Failed to count the number of lines in financial output file {}. Writing into the new financial output file {}", latestModifiedFile.getAbsolutePath(), currentFileName, e);
            }
        } else {
            currentFileName = generateOutputFileName();

            log.info("Writing into the new financial output file {}", currentFileName);
        }

        // verify, if we need to generate a new output file
        if (currentRecordCount.get() >= appConfig.getFinancial().getMaxRecordsPerFile()) {
            log.info("Limit of the lines is reached in the file {}, let's start using a new file", currentFileName);

            currentFileName = generateOutputFileName();
            currentRecordCount.set(0);
        }

        // generate records for CSV from orders
        final List<FinancialOrderRecord> orderRecords = new ArrayList<>();
        orderRecords.addAll(IntStream.range(0, currentRecordCount.get()).mapToObj(i -> (FinancialOrderRecord) null).toList()); // reserve space for same amount of records as in existing output file
        orderRecords.addAll(fromOrder(order));

        // split all records into chunks by appConfig.getFinancial().getMaxRecordsPerFile()
        final List<List<FinancialOrderRecord>> chunks = ListUtils.partition(orderRecords, appConfig.getFinancial().getMaxRecordsPerFile());

        for (List<FinancialOrderRecord> chunk : chunks) {
            final File file = new File(appConfig.getFinancial().getOutputDirectory(), currentFileName);

            log.info("Writing {} records into the financial output file {}", chunk.size(), file);

            final String[] columns = {"order_id", "product_name", "product_id", "quantity", "product_price", "order_total", "order_paid_amount", "currency_code"};

            final boolean isNewFile = !file.exists();

            if (isNewFile) {
                // write header if it's a new file
                try (FileWriter fileWriter = new FileWriter(file)) {
                    final CSVWriter csvWriter = new CSVWriter(fileWriter, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);
                    csvWriter.writeNext(columns);
                    csvWriter.close();
                }
            }

            final ColumnPositionMappingStrategy<FinancialOrderRecord> mappingStrategy = new ColumnPositionMappingStrategy<>();
            mappingStrategy.setType(FinancialOrderRecord.class);
            mappingStrategy.setColumnMapping(columns);

            // reopen the writer for appending the data
            try (FileWriter dataWriter = new FileWriter(file, true)) {
                final StatefulBeanToCsv<FinancialOrderRecord> beanWriter = new StatefulBeanToCsvBuilder<FinancialOrderRecord>(dataWriter)
                        .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                        .withMappingStrategy(mappingStrategy)
                        .build();

                final List<FinancialOrderRecord> records = chunk.stream().filter(Objects::nonNull).collect(Collectors.toList());

                beanWriter.write(records);

                dataWriter.flush();
            }
        }
    }
}