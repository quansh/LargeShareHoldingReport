package gov.sec.form13f;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

/**
 * PDF Extractor for SEC 13f Securities list
 */
public class PDFExtractor {
    /** The start page index of PDF data */
    private static final int START_PAGE_INDEX = 3;

    /** The full path of input PDF file */
    private File pdfFile;

    /** The full path of extracted data file from input PDF file */
    private File dataFile;

    /** The full path of result file */
    private File resultFile;

    /** The extents of extracted data file */
    private static final String PDF_DATA_FILE_POSTFIX = ".data";

    /** Prefix string of title line */
    private static final String TITLE_LINE_PREFIX = "CUSIP";

    /** The following rows number after title line */
    private static final int TITLE_LINE_SUBROWS = 3;

    /** Prefix string of end line */
    private static final String END_LINE_PREFIX = "Total Count";

    /** Line separator */
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    
    /** CSV separator */
    private static final String CSV_SEPARATOR = ",";
    
    /** Column width for CUSIP NO */
    private static final int CUSIP_NO_COL_WIDTH = 11;
    
    /** Flag string for HAS OPTION COLUMN */
    private static final String HAS_OPTION_FLAG = "*";
    
    /** Empty string */
    private static final String STR_EMPTY = "";
    
    /** Status list for identify status column */
    private static final String[] STATUS_FLAG = {"ADDED", "DELETED"};
    
    /**
     * Constructor
     */
    public PDFExtractor(File pdfFile, File dataFile, File resultFile) {
        this.pdfFile = pdfFile; 
        this.dataFile = dataFile;
        this.resultFile = resultFile;
    }

    /**
     * Extract PDF data to result file
     */
    private void processPDFExtract() throws IOException, FileNotFoundException {
        // Extract PDF data from PDF input file
        String docText = null;
        PDDocument doc = null;
        try (InputStream input = new FileInputStream(pdfFile)) {
            PDFParser parser = new PDFParser(input);
            parser.parse();
            doc = parser.getPDDocument();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(START_PAGE_INDEX);
            docText = stripper.getText(doc);
        } finally {
            if (doc != null) {
                doc.close();
            }
        }

        // Write out result data
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(dataFile)); 
             PrintWriter pw = new PrintWriter(writer);) {
            pw.write(docText);
        }
    }

    /**
     * Clean PDF data to CSV data
     * 
     * @throws IOException
     * @throws FileNotFoundException
     */
    private void processDataClean() throws FileNotFoundException, IOException {
        String line = null;
        try (BufferedReader br = new BufferedReader(new FileReader(dataFile));
             BufferedWriter bw = new BufferedWriter(new FileWriter(resultFile));) {

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    // skip
                } else {
                    if (line.startsWith(TITLE_LINE_PREFIX)) {
                        // skip title rows for title line
                        for(int i = 0; i < TITLE_LINE_SUBROWS; i++) {
                            br.readLine();
                        }
                    } else if (line.startsWith(END_LINE_PREFIX)) {
                        // skip
                    } else {
                        // extract data
                        bw.write(sliceLineData(line));
                        bw.write(LINE_SEPARATOR);;
                    }   
                }
            }
        }
    }

    /**
     * Slice out column data to CSV format 
     */
    private String sliceLineData(String line) {
        StringBuilder result = new StringBuilder();
        
        // CUSIP NO
        String cusipNo = line.substring(0, CUSIP_NO_COL_WIDTH);
        
        // OPTION
        String dataAfterCusipNo = line.substring(CUSIP_NO_COL_WIDTH).trim();
        boolean hasOptions = dataAfterCusipNo.startsWith(HAS_OPTION_FLAG);
        dataAfterCusipNo = dataAfterCusipNo.substring(hasOptions ? 1 : 0).trim();
        
        // STATUS
        String status = STR_EMPTY;
        for(int i = 0; i < STATUS_FLAG.length; i++) {
            if(dataAfterCusipNo.endsWith(STATUS_FLAG[i])) {
                status = STATUS_FLAG[i];
                dataAfterCusipNo = dataAfterCusipNo.substring(0, dataAfterCusipNo.length() - status.length()).trim();
                break;
            }
        }
        
        // ISSUER
        String issuer = dataAfterCusipNo;
        
        // CSV Data
        result.append(cusipNo)
              .append(CSV_SEPARATOR)
              .append(hasOptions ? HAS_OPTION_FLAG : STR_EMPTY)
              .append(CSV_SEPARATOR)
              .append(issuer)
              .append(CSV_SEPARATOR)
              .append(status);
        
        return result.toString();
    }

    /**
     * Main process handler
     */
    public static void main(String[] args) {
        if (args.length > 0) {
            File pdfFile = new File(args[0]);
            File dataFile = new File(args[0] + PDF_DATA_FILE_POSTFIX);
            File resultFile = new File(args[1]);

            try {
                PDFExtractor extractor = new PDFExtractor(pdfFile, dataFile,
                        resultFile);
                extractor.processPDFExtract();
                extractor.processDataClean();
                dataFile.delete();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

}
