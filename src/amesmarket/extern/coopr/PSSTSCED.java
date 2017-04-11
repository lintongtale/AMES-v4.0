/*
 * FIXME <LICENCE>
 */
package amesmarket.extern.coopr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import amesmarket.AMESMarket;
import amesmarket.AMESMarketException;
import amesmarket.GenAgent;
import amesmarket.INIT;
import amesmarket.SCED;
import amesmarket.Support;
import amesmarket.TransGrid;
import amesmarket.filereaders.AbstractConfigFileReader;
import amesmarket.filereaders.BadDataFileFormatException;

/**
 * Setup and run the SCED using the PSST program
 *
 * TODO-X : Unify format of each section in the output file.
 *
 * @author Dheepak Krishnamurthy
 *
 */
public class PSSTSCED implements SCED {

    //TODO-XX : Make this client configurable
    private final File scedResourcesDir = new File("SCUCresources");
    private final File scedScript = new File("SCED.py");
    private final File ucVectorFile;
    private final File refModelFile;
    private final File scedFile;

    private final AMESMarket ames;

    /**
     * Commitments by hour, for each genco.
     */
    private double[][] dailyCommitment;
    private double[][] shutdownCost;
    private double[][] startupCost;
    private double[][] productionCost;
    private double[][] dailyLMP;
    private double[][] voltageAngles;
    private double[][] branchFlow;
    private double[][] dailyPriceSensitiveDemand;
    private int[] hasSolution;

    //If future modifaction needs more than just baseS from from the INIT
    //class, we could change the code to keep an instance to the INIT instance.
    private final double baseS;
    private final TransGrid grid;

    private final int hoursPerDay;
    private final int numBranches;

    private final int K;
    private final int N;
    private final int I;
    private final int J;
    private final int H;
    private final boolean deleteFiles;

    /**
     * @param ames market instance begin used.
     * @param init init instance -- used to get the BaseS for PU/SI conversions.
     * @param ucVectorFile File the SCUC writes with the unit commitment information
     * @param refModelFile ReferenceModel file
     * @param outFile     output file for the SCED
     */
    public PSSTSCED(AMESMarket ames, INIT init, File ucVectorFile, File refModelFile, File outFile) {
        this(ames, init.getBaseS(), ucVectorFile, refModelFile, outFile);
    }

    /**
     * @param ames market instance begin used.
     * @param baseS value for PU/SI conversions.
     * @param ucVectorFile File the SCUC writes with the unit commitment information
     * @param refModelFile ReferenceModel file
     * @param outFile     output file for the SCED
     */
    public PSSTSCED(AMESMarket ames, double baseS, File ucVectorFile, File refModelFile, File outFile) {
        this.ucVectorFile = ucVectorFile;
        this.refModelFile = refModelFile;
        this.scedFile = outFile;
        this.baseS = baseS;
        this.grid = ames.getTransGrid();
        this.ames = ames;

        this.hoursPerDay = ames.NUM_HOURS_PER_DAY;
        this.numBranches = ames.getNumBranches();

        K = ames.getNumNodes();
        N = numBranches;
        I = ames.getNumGenAgents();
        J = ames.getNumLSEAgents();
        H = hoursPerDay; //shorter name for local refs.

        deleteFiles = ames.isDeleteIntermediateFiles();
    }

    /**
     * Allocate new space for each a solution.
     * Must be called every time a new solution is created
     * to prevent aliasing problems.
     */
    private void createSpaceForSols() {
        dailyCommitment = new double[H][I];
        shutdownCost = new double[H][I];
        startupCost = new double[H][I];
        productionCost = new double[H][I];
        dailyLMP        = new double[H][K];
        voltageAngles   = new double[H][N];
        branchFlow      = new double[H][N];
        dailyPriceSensitiveDemand = new double [H][J];


        hasSolution = new int[H];
    }

    /**
     * Convert the PU back to SI.
     */
    private void convertToSI() {

        //helper method since we need to do this several times.
        //grumble...lambda functions...grumble
        class Converter {
            /**
             * Multiply or divide each element in vs by baseS
             * @param vs values to covert
             * @param baseS
             * @param mult if true, multiply. if false divide.
             */
            final void convert(double[][] vs, double baseS, boolean mult) {
                for (int i = 0; i < vs.length; i++) {
                    for (int k = 0; k < vs[i].length; k++){
                        if(mult){
                            vs[i][k] *= baseS;
                        }
                        else {
                            vs[i][k] /= baseS;
                        }
                    }
                }
            }

            /**
             * Like {@link #convert}, but with default of multiplication set to true.
             * @param vs
             * @param baseS
             */
            final void convertM(double[][] vs, double baseS){
                convert(vs, baseS, true);
            }

            /**
             * Like {@link #convert}, but with default of multiplication set to false.
             * @param vs
             * @param baseS
             */
            final void convertD(double[][] vs, double baseS) {
                convert(vs, baseS, false);
            }
        }

        Converter c = new Converter();

        c.convertM(dailyCommitment, baseS);
        c.convertM(branchFlow, baseS);
        c.convertD(dailyLMP, baseS);
        c.convertM(dailyPriceSensitiveDemand, baseS);
    }

    /* (non-Javadoc)
     * @see amesmarket.SCED#solveOPF()
     */
    @Override
    /**
     * Assumes all of the necessary data files have been written prior
     * to solving.
     */
    public void solveOPF() throws AMESMarketException {
        //allocate new arrays for this solution.
        createSpaceForSols();

        //Bootstrap system call to run the SCED.py
        try {
            int resCode = runPSSTSCED();
            System.out.println("SCED Result code: " + resCode);
            if (resCode != 0) {
                throw new RuntimeException(
                        "External SCEC exited with non-zero result code "
                                + resCode);
            }
        } catch (IOException e1) {
            throw new AMESMarketException(e1);
        } catch (InterruptedException e1) {
            throw new AMESMarketException(e1);
        }

        //read result file
        try {
            readResults(scedFile);
            computeBranchFlow();
            convertToSI();
        } catch (Exception e) {
            throw new AMESMarketException(e); //FIXME handle the exception sensibly.
        }

        cleanup();
    }



    private int runPSSTSCED() throws IOException, InterruptedException {
        //Process Builder.
        ProcessBuilder pb = new ProcessBuilder(
                "psst",
                "sced",
                "--uc", "'" + ucVectorFile.getAbsolutePath() + "'",
                "--data", "'" + refModelFile.getAbsolutePath() + "'",
                "--output", "'" + scedFile.getAbsolutePath() + "'"
        );
        pb.directory(scedResourcesDir);

        Process p = pb.start();

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(p.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(p.getErrorStream()));

        // read the output from the command
        String s = null;
        System.out.println("Here is the standard output of the command:\n");
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }

        // read any errors from the attempted command
        System.err.println("Here is the standard error of the command (if any):\n");
        while ((s = stdError.readLine()) != null) {
            System.out.println(s);
        }

        int resCode = p.waitFor();

        return resCode;
    }

    private void cleanup() {
        if (deleteFiles ) {
            List<File> filesToRm = Arrays.asList(
                    ucVectorFile,
                    refModelFile,
                    scedFile
                    );
            Support.deleteFiles(filesToRm);
        }
    }

    /**
     * Convert the voltage angles to BranchFlow power, in PU.
     */
    private void computeBranchFlow() {
        double[][] bi = grid.getBranchIndex();
        //TODO-XXX if this is the correct adaptation of DCOPFJ version, get rid of the 'full' name.
        double[][] fullVoltAngle = voltageAngles;

        for (int h = 0; h < hoursPerDay; h++) {
            for (int n = 0; n < numBranches; n++) {
                branchFlow[h][n] = (1 / grid.getReactance()[n])
                        * (fullVoltAngle[h][(int) bi[n][0] - 1] - fullVoltAngle[h][(int) bi[n][1] - 1]);
            }
        }
    }

    private void readResults(File in) throws BadDataFileFormatException {
        SCEDReader scedr = new SCEDReader();
        scedr.read(in);
    }

    /**
     * @return the scedResourcesDir
     */
    public File getScedResourcesDir() {
        return scedResourcesDir;
    }

    /**
     * @return the ucVectorFile
     */
    public File getUcVectorFile() {
        return ucVectorFile;
    }

    /**
     * @return the refModelFile
     */
    public File getRefModelFile() {
        return refModelFile;
    }

    /**
     * @return the scedFile
     */
    public File getScedFile() {
        return scedFile;
    }

    ////////////////////BEGIN SCED///////////////////////////
    /* (non-Javadoc)
     * @see amesmarket.SCED#getDailyCommitment()
     */
    @Override
    public double[][] getDailyCommitment() {
        return dailyCommitment;
    }

    /* (non-Javadoc)
     * @see amesmarket.SCED#getDailyLMP()
     */
    @Override
    public double[][] getDailyLMP() {
        return dailyLMP;
    }

    /* (non-Javadoc)
     * @see amesmarket.SCED#getDailyBranchFlow()
     */
    @Override
    public double[][] getDailyBranchFlow() {
        return branchFlow;
    }

    /* (non-Javadoc)
     * @see amesmarket.SCED#getDailyPriceSensitiveDemand()
     */
    @Override
    public double[][] getDailyPriceSensitiveDemand() {
        return dailyPriceSensitiveDemand;
    }

    /* (non-Javadoc)
     * @see amesmarket.SCED#getHasSolution()
     */
    @Override
    public int[] getHasSolution() {
        return hasSolution;
    }

    /**
     * @return the shutdownCost
     */
    public double[][] getShutdownCost() {
        return shutdownCost;
    }

    /**
     * @return the startupCost
     */
    public double[][] getStartupCost() {
        return startupCost;
    }

    /**
     * @return the productionCost
     */
    public double[][] getProductionCost() {
        return productionCost;
    }
    ////////////////////END SCED///////////////////////////

    /**
     * Helper class to read the data file. It modifies the
     * fields of the its parent class as it reads.
     * @author Sean L. Mooney
     */
    private class SCEDReader extends AbstractConfigFileReader<Void> {

        /**
         * Read the LMPs for each zone. Will need to divide by the baseS parameter to get these values to sensible $/MWh.
         */
        private void readLMP() throws BadDataFileFormatException {
            double[][] lmp = dailyLMP; //don't lookup the parent reference all the time.
            do {
                move(true);
                if(endOfSection(LMP)) break;

                //This section is formatted a little differently than the gencos.
                String[] p = currentLine.split(DELIM);
                Support.trimAllStrings(p);

                //assume p[0] looks like 'BusN'
                // TODO - fix below for more than 9 buses
                p[0] = p[0].substring(p[0].length() - 1, p[0].length());
                int h = stoi(p[1]) - 1; //adjust for array index
                int b = stoi(p[0]) - 1;
                //deal with the current line to find the lmp.
                lmp[h][b] = stod(p[2]);
                //sced.lmp[branch][hour] ?or the other way around = value
            }while(true);
        }

        /**
         * Read the dispatch level, in PU from PSST.
         */
        private void readGenCoResults() throws BadDataFileFormatException {
            double[][] dispatch = dailyCommitment; //don't lookup the parent reference all the time.
            double[][] shutdownCost = PSSTSCED.this.shutdownCost;
            double[][] startupCost = PSSTSCED.this.startupCost;
            double[][] productionCost = PSSTSCED.this.productionCost;


            //key/value indexes for the data in this section.
            final int KIDX = 0;
            final int VIDX = 1;
            int curGenCoIdx = 0;
            int curHour = 0;

            do {
                move(true);
                if(endOfSection(GENCO_RESULTS))
                    break;

                //first check for a 'new' genco line
                //Assume the list of commitments for each GenCo is
                // GenCoX where x is the index in the array/genco number
                // followed by a list of genco elements.

                if(currentLine.startsWith(GEN_CO_LABEL)){
                    //lookup the label's idx
                    GenAgent ga = ames.getGenAgentByName(currentLine);

                    if(ga == null) {
                        throw new BadDataFileFormatException(
                                "Unknown GenCo " + currentLine + " in " + sourceFile.getPath());
                    }

                    curGenCoIdx = ga.getIndex();
                } else {
                    //should find either Hour, PowerGenerated, ProductionCost, StartupCost, or ShutdownCost.
                    //these are all label ':' value
                    String[] keyAndValue = splitKeyValue(DELIM, currentLine, true);
                    try{
                        if(HOUR.equals(keyAndValue[KIDX])) {
                            curHour = Integer.parseInt(keyAndValue[VIDX]);
                            if( curHour > 0 && curHour <= hoursPerDay )
                                curHour = curHour -1; //adjust for array index repr.
                            else
                                throw new BadDataFileFormatException(lineNum,
                                        "Invalid hour for GenCo" + curGenCoIdx +
                                        " Encountered hour " + curHour);
                        } else if (POWER_GEN.equals(keyAndValue[KIDX])) {  //PowerGenerated
                            dispatch[curHour][curGenCoIdx] = Double.parseDouble(keyAndValue[VIDX]);
                        } else if (PRODUCTION_COST.equals(keyAndValue[KIDX])) { //Production Cost
                            productionCost[curHour][curGenCoIdx] = Double.parseDouble(keyAndValue[VIDX]);
                        } else if (STARTUP_COST.equals(keyAndValue[KIDX])) { //Startup Cost
                            startupCost[curHour][curGenCoIdx] = Double.parseDouble(keyAndValue[VIDX]);
                        } else if (SHUTDOWN_COST.equals(keyAndValue[KIDX])) { //Shutdown Cost
                            shutdownCost[curHour][curGenCoIdx] = Double.parseDouble(keyAndValue[VIDX]);
                        } else {
                            throw new BadDataFileFormatException(lineNum, "Unknown label " + keyAndValue[KIDX]);
                        }
                    } catch(NumberFormatException nfe) {
                        //the only thing that throws a NumberFormatExecption is parsing the value of the key/value pairs.
                        throw new BadDataFileFormatException(lineNum,
                                "Invalid value " + keyAndValue[VIDX]);
                    }
                }

            }while(true);
        }

        /**
         * Read the voltage angle at each Bus, for each Hour, in radians.
         */
        private void readVoltageAngles() throws BadDataFileFormatException {
            final int BUS_LEN = 3; //length of the word Bus

            do {
                int busNum = -1;
                int hour = -1;
                double voltageAngle = 0;

                move(true);
                if(endOfSection(VOLTAGE_ANGLES))
                    break;

                String[] p = currentLine.split(":");
                Support.trimAllStrings(p);

                if(p.length != 2){
                    throw new BadDataFileFormatException("Expected BusX <hour> : <angle>. Found " + currentLine);
                }

                String[] busAndHour = p[0].split(WS_REG_EX);
                Support.trimAllStrings(busAndHour);
                if(busAndHour.length != 2) {
                    throw new BadDataFileFormatException("Could not find and bus and hour in" + p[0] + ".");
                }

                //assume that each bus starts with the word Bus
                try {
                    busNum = Integer.parseInt(
                            busAndHour[0].substring(BUS_LEN)
                            );
                } catch(NumberFormatException nfe){
                    throw new BadDataFileFormatException(nfe);
                }

                //parse the hour
                try {
                    hour = Integer.parseInt( busAndHour[1] );
                    hour = hour - 1; //adjust for the 1-24 representation in the data file.
                } catch(NumberFormatException nfe){
                    throw new BadDataFileFormatException(nfe);
                }

                //parse the actual angle.
                try {
                    voltageAngle = Double.parseDouble( p[1] );
                } catch(NumberFormatException nfe){
                    throw new BadDataFileFormatException(nfe);
                }

                voltageAngles[hour][busNum] = voltageAngle;
            }while(true);
        }

        private void readBranchLMP() throws BadDataFileFormatException {
            do {
                move(true);
                //deal with the current line to find the lmp.
                //sced.lmp[branch][hour] ?or the other way around = value
            }while(!endOfSection(BRANCH_LMP));
        }

        private void readPriceSensitiveDemand() throws BadDataFileFormatException {
            do {
                move(true);
                //deal with the current line to find the lmp.
                //sced.lmp[branch][hour] ?or the other way around = value
            }while(!endOfSection(PRICE_SENSITIVE_DEMAND));
        }

        private void readHasSolution() throws BadDataFileFormatException {
            int[] hasSols = hasSolution; //local copy.
            move(true);
            //assume the next line is vector with 1 entry for each hour.
            String[] vs = currentLine.split("\\s+");
            if(vs.length != hasSols.length)
                throw new BadDataFileFormatException(
                        String.format("Expected %d found %d in hasSolution vector from the external SCED.",
                                hasSols.length, vs.length
                                ));

            //now that we know there are the expected number of hasSolution elems in vs.
            for(int i = 0; i<hasSols.length; i++){
                try{
                    int s = Integer.parseInt(vs[i]);

                    if(!(s == 0 || s==1)){
                        throw new BadDataFileFormatException(lineNum,
                                "Invalid hasSolution marker. Expected 0/1, got " + vs[i]);
                    }

                    hasSolution[i] = s;
                }catch(NumberFormatException nfe){
                    throw new BadDataFileFormatException(lineNum,
                            "Invalid hasSolution marker. Expected 0/1, got " + vs[i]);
                }

            }

            //Look for the section end marker
            move(true);
            if(!endOfSection(HAS_SOLUTION)){
                throw new BadDataFileFormatException(lineNum,
                        "Expected END" + HAS_SOLUTION + ". Found " + currentLine + "."
                        );
            }
        }

        private boolean endOfSection(String secName){
            return (END + secName).equals(currentLine);
        }

        @Override
        protected Void read() throws BadDataFileFormatException {

            while (true) {
                move(false);
                if (currentLine == null)
                    break;

                if (LMP.equals(currentLine)) {
                    readLMP();
                } else if (GENCO_RESULTS.equals(currentLine)){
                    readGenCoResults();
                } else if (VOLTAGE_ANGLES.equals(currentLine)) {
                    readVoltageAngles();
                } else if (BRANCH_LMP.equals(currentLine)) {
                    readBranchLMP();
                } else if (PRICE_SENSITIVE_DEMAND.equals(currentLine)) {
                    readPriceSensitiveDemand();
                } else if (HAS_SOLUTION.equals(currentLine)) {
                    readHasSolution();
                } else {
                    throw new BadDataFileFormatException(lineNum, currentLine);
                }
            }


            return null;
        }

        private static final String LMP = "LMP";
        private static final String GENCO_RESULTS = "GenCoResults";
        private static final String VOLTAGE_ANGLES = "VOLTAGE_ANGLES";
        private static final String BRANCH_LMP = "DAILY_BRANCH_LMP";
        private static final String PRICE_SENSITIVE_DEMAND = "DAILY_PRICE_SENSITIVE_DEMAND";
        private static final String HAS_SOLUTION = "HAS_SOLUTION";
        private static final String HOUR = "Hour";
        private static final String DELIM = ":";
        //GenCo data labels/tokens
        private static final String GEN_CO_LABEL = "GenCo";
        private static final String POWER_GEN = "PowerGenerated";
        private static final String PRODUCTION_COST = "ProductionCost";
        private static final String STARTUP_COST = "StartupCost";
        private static final String SHUTDOWN_COST = "ShutdownCost";
        //End GenCo data labels.
        private static final String END = "END_"; //section end marker

    }
}

