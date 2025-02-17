package com.example.ecommerceorderprocessor.service;

import com.example.ecommerceorderprocessor.config.AppConfig;
import com.example.ecommerceorderprocessor.model.Order;
import com.example.ecommerceorderprocessor.model.financial.FinancialOrderRecord;
import com.opencsv.CSVWriter;
import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import jakarta.validation.constraints.NotEmpty;
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
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialService {

    private final AppConfig appConfig;

    private static int countLines(File file) throws IOException {
        if (file.exists()) {
            try (Stream<String> lines = Files.lines(Paths.get(file.getAbsolutePath()))) {
                return (int) lines.count() - 1;
            }
        } else {
            return 0;
        }
    }

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

    private static File getRecentlyModifiedFile(@NotEmpty String outputDirectory, @NotEmpty String fileNamePattern) {
        final File[] files = new File(outputDirectory).listFiles();

        return Arrays.stream(files != null ? files : new File[0])
                .filter(File::isFile)
                .filter(file -> {
                    final String extension = StringUtils.substringAfterLast(fileNamePattern, ".");

                    return StringUtils.equalsIgnoreCase(FilenameUtils.getExtension(file.getName()), extension);
                })
                .filter(file -> {
                    final String outputFileNamePrefix = StringUtils.substringBefore(fileNamePattern, "{");

                    return StringUtils.startsWithIgnoreCase(file.getName(), outputFileNamePrefix);
                })
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }

    private static void writeChuckToFile(List<FinancialOrderRecord> chunk, File currentOutputFile) throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        final List<FinancialOrderRecord> records = chunk.stream().filter(Objects::nonNull).collect(Collectors.toList());

        log.info("Writing {} records into the financial output file {}", records.size(), currentOutputFile);

        final String[] columns = {"order_id", "product_name", "product_id", "quantity", "product_price", "order_total", "order_paid_amount", "currency_code"};

        final boolean isNewFile = !currentOutputFile.exists();

        if (isNewFile) {
            // write header if it's a new file
            try (FileWriter fileWriter = new FileWriter(currentOutputFile)) {
                final CSVWriter csvWriter = new CSVWriter(fileWriter, CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);
                csvWriter.writeNext(columns);
                csvWriter.close();
            }
        }

        final ColumnPositionMappingStrategy<FinancialOrderRecord> mappingStrategy = new ColumnPositionMappingStrategy<>();
        mappingStrategy.setType(FinancialOrderRecord.class);
        mappingStrategy.setColumnMapping(columns);

        // reopen the writer for appending the data
        try (FileWriter dataWriter = new FileWriter(currentOutputFile, true)) {
            final StatefulBeanToCsv<FinancialOrderRecord> beanWriter = new StatefulBeanToCsvBuilder<FinancialOrderRecord>(dataWriter)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .withMappingStrategy(mappingStrategy)
                    .build();

            beanWriter.write(records);

            dataWriter.flush();
        }
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

    private String getCurrentFileName() {
        // find latest modified file in output directory
        final File recentlyModifiedFile = getRecentlyModifiedFile(appConfig.getFinancial().getOutputDirectory(), appConfig.getFinancial().getFileNamePattern());

        String currentFileName;

        if (recentlyModifiedFile != null) {
            currentFileName = recentlyModifiedFile.getName();

            log.info("Attempt to continue writing into recently modified financial output file {}", currentFileName);
        } else {
            currentFileName = generateOutputFileName();

            log.info("Writing into the new financial output file {}", currentFileName);
        }

        return currentFileName;
    }

    private File resolveOutputFile() {
        String currentFileName = getCurrentFileName();

        int lines = 0;

        try {
            final File f = new File(appConfig.getFinancial().getOutputDirectory(), currentFileName);

            if (f.exists()) {
                lines = countLines(f);

                log.info("The financial output file {} has {} lines", currentFileName, lines);
            }
        } catch (IOException e) {
            currentFileName = generateOutputFileName();

            log.error("Failed to count the number of lines in previous financial output file. Writing into the new financial output file {}", currentFileName, e);
        }

        if (lines >= appConfig.getFinancial().getMaxRecordsPerFile()) {
            final String previousFileName = currentFileName;

            currentFileName = generateOutputFileName();

            if (previousFileName.equals(currentFileName)) {
                final String msg = MessageFormat.format("Cannot continue to write orders into the financial files," +
                        " because the limit of the records in the file {0} is reached its limit of {1} records and the " +
                        "rules for file naming are not allowing us to generate a unique file name. You might want to" +
                        "repeat the operation a bit later.", previousFileName, appConfig.getFinancial().getMaxRecordsPerFile());

                log.error(msg);

                throw new IllegalStateException(msg);
            } else {
                log.info("Limit of the lines is reached in the file {}, let's start using a new file {}", previousFileName, currentFileName);
            }
        }

        return new File(appConfig.getFinancial().getOutputDirectory(), currentFileName);
    }

    @SneakyThrows
    public void writeOrderToFile(final Order order) {
        ensureOutputDirectoryExistsAndValidate();

        // resolve current output file and verify its size
        File currentOutputFile = resolveOutputFile();

        // generate records for CSV from orders
        final List<FinancialOrderRecord> orderRecords = new ArrayList<>();
        // NB! reserve space for same amount of records as in existing output file
        orderRecords.addAll(IntStream.range(0, countLines(currentOutputFile)).mapToObj(i -> (FinancialOrderRecord) null).toList());
        orderRecords.addAll(fromOrder(order));

        // split all records into chunks by appConfig.getFinancial().getMaxRecordsPerFile()
        final List<List<FinancialOrderRecord>> chunks = ListUtils.partition(orderRecords, appConfig.getFinancial().getMaxRecordsPerFile());

        for (Iterator<List<FinancialOrderRecord>> iterator = chunks.iterator(); iterator.hasNext(); ) {
            final List<FinancialOrderRecord> chunk = iterator.next();

            writeChuckToFile(chunk, currentOutputFile);

            if (iterator.hasNext()) {
                // next chunk should be writen into a new file (but we want to make sure, that new file does not overlap with existing file)
                currentOutputFile = resolveOutputFile();
            }
        }
    }
}