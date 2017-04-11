/* ============================================================================
 * AMES Wholesale Power Market Test Bed (Java): A Free Open-Source Test-Bed
 *         for the Agent-based Modeling of Electricity Systems
 * ============================================================================
 *
 * (C) Copyright 2008, by Hongyan Li, Junjie Sun, and Leigh Tesfatsion
 *
 *    Homepage: http://www.econ.iastate.edu/tesfatsi/AMESMarketHome.htm
 *
 * LICENSING TERMS
 * The AMES Market Package is licensed by the copyright holders (Junjie Sun,
 * Hongyan Li, and Leigh Tesfatsion) as free open-source software under the
 * terms of the GNU General Public License (GPL). Anyone who is interested is
 * allowed to view, modify, and/or improve upon the code used to produce this
 * package, but any software generated using all or part of this code must be
 * released as free open-source software in turn. The GNU GPL can be viewed in
 * its entirety as in the following site: http://www.gnu.org/licenses/gpl.html
 */

// ISO.java
// Independent system operator

package amesmarket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import amesmarket.extern.coopr.DataFileWriter;
import amesmarket.extern.coopr.PyomoSCED;
import amesmarket.extern.coopr.CooprSCUC;
import amesmarket.extern.common.CommitmentDecision;
import amesmarket.filereaders.BadDataFileFormatException;

import amesmarket.extern.psst.PSSTSCUCSolver;
import amesmarket.extern.psst.PSSTSCEDSolver;

/**
 * Independent system operator.
 *
 */
public class ISO {

    // ISO's data;
    private int currentMonth;   //to check if it has been a month

    private double[][] supplyOfferByGen;
    private double[][] loadProfileByLSE;
    private double[][] nextDayLoadProfileByLSE;
    private double[][][] demandBidByLSE;
    private int   [][] demandHybridByLSE;
    private double[][] supplyOfferByGenRealTime;
    private double[][] committedLoadByLSERealTime;
    private double[][][] demandBidByLSERealTime;
    private List<CommitmentDecision> genSchedule,genScheduleRT;
    private int   [][] demandHybridByLSERealTime;
    private double[][] dailyPriceSensitiveDispatch,dailyPriceSensitiveDispatchRT;
    private double[][] dailycommitment,supplyOfferRT;
    private double[][] dailyRTcommitment, dailyRTlmp, dailyRTbranchflow;
    private double[][] dailyRTProductionCost;
    private double[][] dailyRTStartupCost;
    private double[][] dailyRTShutdownCost;
    private double[][] dailylmp;
    private double[][] dailyBranchFlow;
    private ArrayList commitmentListByDay; // hourly commitments list for each agent by day (d)
    private ArrayList lmpListByDay;        // hourly LMPs list for each bus by day (d)

    private static final int A_INDEX    = 0;
    private static final int B_INDEX    = 1;
    private static final int CAP_LOWER  = 2;
    private static final int CAP_UPPER  = 3;


    private AMESMarket ames;
    private DAMarket dam;
    private SRMarket srm;
    private RTMarket rtm;
    private FTRMarket ftrm;
    private ScenarioGenerator scengen;
    // private CooprSCUC scuc;
    private PSSTSCUCSolver scuc;
    private SCED sced;
    private BUC buc;
    /**
     * Num hours per day.
     */
    private final int H;
    private final int maxDay;
    /**
     * Num genagents.
     */
    private final int I;
    /**
     * Num LSE agents.
     */
    private final int J;

    private int randomSeed;
    private int noOfScenarios;
    private Random randomGenerator;
    

    // constructor
    public ISO(AMESMarket model, INIT init) throws IOException {

        //System.out.println("Creating the ISO object: iso \n");

        commitmentListByDay = new ArrayList();
        lmpListByDay        = new ArrayList();
        ames  = model;
        maxDay = ames.DAY_MAX;
        H = ames.NUM_HOURS_PER_DAY;
        I = ames.getNumGenAgents();
        J = ames.getNumLSEAgents();
        supplyOfferRT=new double[I][4];
        //dailyPriceSensitiveDispatchRT=new double[H][J];
        
        //genSchedule = new int[I][H];
        //genScheduleRT = new int[I][H];

        dam  = new DAMarket(ames);
        srm  = new SRMarket(ames);
        rtm  = new RTMarket(this,ames);
        ftrm = new FTRMarket(ames);
        buc = new BUC(this,ames, init);
        scuc = new PSSTSCUCSolver(this, ames);

        rtm.setBUC(buc);

        String scedProp = System.getProperty("SCED", "pyomo");
        if ("pyomo".equals(scedProp)) {
            System.out.println("Using the external pyomo SCED.");
            sced = new PSSTSCEDSolver(this, ames);
        } else if ("dcopfj".equals(scedProp)) {
            System.out.println("Using DCOPFJ SCED.");
            sced = buc;
        } else {
            //TODO-XX Better error message
            throw new IllegalArgumentException("Unknown SCED engine " + scedProp);
        }

        scengen = new ScenarioGenerator(this,ames);
        //FIXME: SEED
        randomGenerator = new Random(50000);
    }

    public void computeCompetitiveEquilibriumResults() {
        //System.out.println("Compute competitive equilibrium results before the market is run\n");
        //FIXME: Should use the sced, not the buc.
        dam.submitTrueSupplyOffersAndDemandBids();
        supplyOfferByGen = dam.getTrueSupplyOfferByGen();
        loadProfileByLSE = dam.getLoadProfileByLSE();
        nextDayLoadProfileByLSE = dam.getNextDayLoadProfileByLSE();
        demandBidByLSE = dam.getTrueDemandBidByLSE();
        demandHybridByLSE = dam.getDemandHybridByLSE();

        // Carry out BUC (Bid-based Unit Commitment) problem by solving DC OPF problem
        //System.out.printf("Solving the DC-OPF problem \n");
        
//        genSchedule = new ArrayList<SCUC.CommitmentDecision>();
//         for (int i=0;i<I;i++) {
//            int[] schedule = new int[H];
//             for (int j=0;j<H;j++) {
//                schedule[j] = 1;
//                //genSchedule[i][j]=1;
//            }
//            genSchedule.add(new CommitmentDecision("GenCo" + i+1, i, schedule));
//         }
//
//        buc.solveOPF();
//
//        dailycommitment = buc.getDailyCommitment();
//        dailylmp = buc.getDailyLMP();
//        dailyPriceSensitiveDispatch=buc.getDailyPriceSensitiveDemand();
//
//        //NOT update generator's commitment, daily lmp, profit
//        //dam.post(dailycommitment,dailylmp,1);
//
//        ames.addGenAgentCommitmentWithTrueCost(dailycommitment);
//        ames.addLMPWithTrueCost(dailylmp);
//        ames.addLSEAgentPriceSensitiveDemandWithTrueCost(dailyPriceSensitiveDispatch);
//
//        dam.postTrueSupplyOfferAndDemandBids(dailycommitment,dailylmp,dailyPriceSensitiveDispatch);
    }

    /**
     * Operate the wholesale market for day d, hour h
     * @param h market hour
     * @param d market day
     * @throws IOException
     */
    public void wholesalePowerMarketOperation(int h, int d) throws AMESMarketException, IOException {
        final int tomorrow = d + 1;

        if(h==0) {
            if(d == ames.DAY_MAX) {
                System.out.println("\nDay max is " + (d) + ". Day Ahead Market not executing\n");
            } else {
                dam.dayAheadOperation(h, d);
            }
            
            loadProfileByLSE = dam.getLoadProfileByLSE();
            
            if(d<ames.DAY_MAX) {
                nextDayLoadProfileByLSE = dam.getNextDayLoadProfileByLSE();
            }

            /* TODO-XXX:
             * track the initial (before fixing for canaries schedule).
             * postInitialScheduleToGenCos(tomorrow, genSchedule);
             */
            //Comment -- yes genSchedule is a field, but data flow
            //reads much easier if pass it as an argument.
            //e.g. lambda lift.
            //store yesterday's commitment decision for today, with adjusted canaries
            //in the genco records.
            


            //scengen.generateScenarios(d); //FIXME: DELETE ScenarioGen here!
            if (d != 1) {
                rtm.realTimeOperation(h, d);
                rtm.evaluateRealTimeBidsOffers(genScheduleRT,
                        getRealTimeLoad(h, d), h, d);
                postRealTimeSolutions(d);
            }

            sanityCheck(d);
            
        } else if (h == 1) {
            if (d == ames.DAY_MAX){
                System.out.println("\nDay max is " + (d) + ". SCUC not executing\n");
            }
            else 
            {
            System.out.println("SCUC for DAY " + tomorrow + " executing");
            scuc.calcSchedule(tomorrow);
            genSchedule = scuc.getSchedule();
            }
        }

//        if(h==12) {
//            evaluateBidsOffers(h,d);
//        }
//        if(h==17) {
//            if(d==321) {
//                int stop=1;
//            }
//            initialPost(h,d);
//            //srm.supplyReOfferOperation(h,d+1,m);
//        }
//        if(h==18) {
//            //produceCommitmentSchedule(h,d+1,m);
//        }

        if (d != 1 && h==23) {
           /* rtm.evaluateRealTimeBidsOffers(genScheduleRT, supplyOfferRT,
                    dailyPriceSensitiveDispatchRT[h], h, d);
                    */
        }

        //update any status listeners.
        announceHourlyLoads(h, d);

        if(h==23 && (d < ames.DAY_MAX)) {
            endOfDayCleanup();
            postScheduleToGenCos(tomorrow, genScheduleRT);
        }


        //rtm.realTimeOperation(h,d,dailycommitmentRT[h],dailyPriceSensitiveDispatch[h]);

    }

    /**
     * Take care of the data transfers that happend at the end of the day.
     */
    public void endOfDayCleanup() {
        for(int i=0; i<I; i++)
            for(int j=0; j<4; j++)
                supplyOfferRT[i][j]=supplyOfferByGen[i][j];
        
        //Copy the commitment schedule over to the RT datastructures.
		for (int i = 0; i < I; i++) {
			genScheduleRT = new ArrayList<CommitmentDecision>();
			for (CommitmentDecision cd : genSchedule) {
				// clone to keep from overwriting the DA data.
				CommitmentDecision rtcd = new CommitmentDecision(cd);

				GenAgent ga = ames.getGenAgentByName(cd.generatorName);

				// Always commit the generator in the realtime market if
				// it is a canary. Work around to ensure enough capacity for any
				// load when a reserve market doesn't exist in the program.
				if (ga.isCanary()) {
					for (int h = 0; h < rtcd.commitmentDecisions.length; h++) {
						rtcd.commitmentDecisions[h] = 1;
					}
				}

				genScheduleRT.add(rtcd);

			}
		}


        for(int i=0; i<H; i++)
            for(int j=0; j<J; j++)
            {
                if (demandHybridByLSE[j][i]!=1)
                {
                    //System.out.println(demandHybridByLSE[j][i]);
                    //FIXME: Crashes if non-fixed demand is turned on.
                    dailyPriceSensitiveDispatchRT[i][j]=dailyPriceSensitiveDispatch[i][j];
                }
            }


    }

    private double[][] getRealTimeLoad(int h, int d) throws AMESMarketException {
        final DailyLoadProfile today = ames.getLoadScenarioProvider().getActualScenario().get(d);

        double[][] rtl = new double[I][];

        for(int zNum = 0; zNum < J; zNum++ ) {
            double[] l = today.getZoneLoad(zNum);
            //TODO-XX: The daily load profile should protect itself from aliasing issues
            //not ad-hoc at usage points.
            rtl[zNum] = Arrays.copyOf(l, l.length); //don't use the real data, in case it changes.
        }

        return rtl;
    }

    /**
     * Send the commitment decisions to the correct GenAgents.
     * @param day
     * @param commDecision
     */
    private void postScheduleToGenCos(int day, List<CommitmentDecision> commDecision) {
        if (commDecision == null) return; //skip if the commDecision null. Nothing to be done.

        for(CommitmentDecision cd : commDecision) {
            //copy the array to make sure the data doesn't get changed accidentally.
            int[] commCopy = Arrays.copyOf(cd.commitmentDecisions, cd.commitmentDecisions.length);

            ames.getGenAgentByName(cd.generatorName).addCommitmentForDay(day, commCopy);
        }
    }

    /**
     * Copy the solutions to the interested parties.
     * @param day
     */
    public void postRealTimeSolutions(int day) {
        dailyRTcommitment = rtm.getRtDispatches(); //buc.getDailyRealTimeCommitment();
        postRTDispatchesToGenAgents(day, dailyRTcommitment);
        dailyRTlmp = rtm.getRtLMPs(); //buc.getDailyRealTimeLMP();
        dailyRTbranchflow = rtm.getRtBranchFlow(); //buc.getDailyRealTimeBranchFlow();

        ames.addRealTimeLMPByDay(dailyRTlmp);
        ames.addGenAgentRealTimeCommitmentByDay(dailyRTcommitment);
        ames.addRealTimeBranchFlowByDay(dailyRTbranchflow);

        dailyRTProductionCost = rtm.getRtProductionCost();
        dailyRTStartupCost = rtm.getRtStartupCost();
        dailyRTShutdownCost = rtm.getRtShutdownCost();
        ames.addActualProductionCostsByDay( dailyRTProductionCost );
        ames.addActualStartupCostsByDay( dailyRTStartupCost );
        ames.addActualShutdownCostsByDay( dailyRTShutdownCost );
        postCostsToGenAgents(day, dailyRTStartupCost, dailyRTProductionCost, dailyRTShutdownCost);
    }

    private void postCostsToGenAgents(final int day, double[][] dailyRTStartupCost,
            double[][] dailyRTProductionCost, double[][] dailyRTShutdownCost) {
        
        final ArrayList<GenAgent> genCos = ames.getGenAgentList();
        for (int gc = 0; gc < I; gc++) {
            final double[] genStartCosts = new double[H];
            final double[] genProductionCosts = new double[H];
            final double[] genShutdownCosts = new double[H];
            
            for (int h = 0; h < H; h++) {
                genStartCosts[h] = dailyRTStartupCost[h][gc];
                genProductionCosts[h] = dailyRTProductionCost[h][gc];
                genShutdownCosts[h] = dailyRTShutdownCost[h][gc];
            }
            
            genCos.get(gc).addDailyCosts(day, 
                    genStartCosts,
                    genProductionCosts,
                    genShutdownCosts
                    );
        }
    }

    /**
     * Post/store the commitment for each hour/genagent.
     * @param dispatches hour X genco grid.
     */
    private void postRTDispatchesToGenAgents(int day, double[][] dispatches) {
        final ArrayList<GenAgent> genCos = ames.getGenAgentList();
        for (int gc = 0; gc < I; gc++) {
            final double[] genDispatches = new double[H];
            for (int h = 0; h < H; h++) {
                genDispatches[h] = dispatches[h][gc];
            }
            genCos.get(gc).addActualDispatch(day, genDispatches);
        }
    }

    /**
     * Print out the values in the loadProfileByLSE array.
     */
    private void printZoneProfiles(int d) {
        //TODO: Logging level trace.
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Day %d--LSE Expected Load\n", d));
        for(int z = 0; z < loadProfileByLSE.length; z++) {
            sb.append("Zone: " + (z+1) + ": ");
            int maxH = loadProfileByLSE[z].length;
            for (int h = 0; h < maxH; h++) {
                String loadDesc = String.format("%1$15.2f", loadProfileByLSE[z][h]);
                sb.append(loadDesc);
                if(h < maxH-1)
                    sb.append("\t");
            }
            sb.append("\n");
        }
        sb.append("End Expected Load\n");

        System.out.println(sb);
    }

    /**
     * Write the computed LMPs to a file.
     * @param logFile
     */
    private void logLMPstoFile(File logFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("LMPs\n");
        for (int i = 0; i < dailylmp.length; i++) {
            double[] lmps = dailylmp[i];
            sb.append("H:"); sb.append(i+1); sb.append(" ");
            for (int z = 0; z < lmps.length; z++) {
                sb.append(lmps[z]);
                if(z < lmps.length - 1) //don't put a comma after the last elem.
                    sb.append(", ");
            }
            sb.append('\n');
        }
        String lmpDesc = sb.toString();
        System.out.println(lmpDesc);
        try {
            FileWriter fw = new FileWriter( logFile );
            fw.write(lmpDesc);
            fw.close();
        } catch (IOException e) {
            System.err.println("Unable to write the LMP Log.\n" + e.getMessage());
        }
    }

    public void evaluateBidsOffers(int h, int d) throws AMESMarketException {
        //System.out.println("Hour " + h + " Day " + d  +
        //                   ": Evaluate LSEs' bids and GenCos' offers.");
        supplyOfferByGen = dam.getSupplyOfferByGen();
        //System.out.println("SupplyOffer b is "+supplyOfferByGen[0][1]);
        loadProfileByLSE = dam.getLoadProfileByLSE();
        nextDayLoadProfileByLSE = dam.getNextDayLoadProfileByLSE();

        //uncomment for debugging.
        //printZoneProfiles(d);

        demandBidByLSE = dam.getDemandBidByLSE();
        demandHybridByLSE = dam.getDemandHybridByLSE();

        // Carry out BUC (Bid-based Unit Commitment) problem by solving DC OPF problem
        //System.out.printf("Solving the DC-OPF problem for day %1$d \n", d+1);
        sced.solveOPF();
        //Realtime OPF

        dailycommitment = sced.getDailyCommitment();  //dailycommittment -> damcommittment
        //System.out.println("Pls check is "+dailycommitment[0][1]);
        dailylmp = sced.getDailyLMP();  //dailylmp -> damlmps
        dailyBranchFlow = sced.getDailyBranchFlow(); //dailybranchflow -> dambranchflow
        dailyPriceSensitiveDispatch=sced.getDailyPriceSensitiveDemand();

        //Check the LMPs coming back
        //Enable for debugging
        //logLMPstoFile(new File("LMP-Log"));

        ames.addLSEAgenPriceSensitiveDemandByDay(dailyPriceSensitiveDispatch);
        ames.addBranchFlowByDay(dailyBranchFlow);
        ames.addGenAgentCommitmentByDay(dailycommitment);
        ames.addLMPByDay(dailylmp);
        ames.addHasSolutionByDay(sced.getHasSolution());
    }

    public void initialPost(int h, int d) {
        //System.out.println("Hour " + h + " Day " + d +
        //                   ": Post hourly commitment schedule and hourly LMPs.");
        dam.post(dailycommitment,dailylmp,dailyPriceSensitiveDispatch,2);

    }

    public void produceCommitmentSchedule(int h, int d) {
        System.out.println("Hour " + h + " Day " + d +
                           ": produce commitment schedule.");
    }

    private void sanityCheck(int day) {
        //make sure that the unit commitment for day d, matches the
        //actually producution. That is, if something wasn't committed
        //and still generated power, there is a problem!

        for(GenAgent ga : ames.getGenAgentList()) {
            ga.sanityCheck(day);
        }
    }

    public void DayAheadMarketCheckLastDayAction() {
        dam.checkGenLastDayAction();
    }

    /**
     * Read the load profile for the day and hour.
     * @param hour
     * @param day
     * @param hourlyLoadProfileByLSE --out parameter
     * @throws FileNotFoundException, IOException
     */
    public void getActualLoadProfile(int hour, int day, double[] hourlyLoadProfileByLSE) throws AMESMarketException {
        LoadProfileCollection actualScenario = ames.getLoadScenarioProvider().getActualScenario();
        DailyLoadProfile dailyProfile = actualScenario.get(day);
        double[] hourProfile = dailyProfile.getLoadByHour(hour);
        if(hourProfile == null) {
            //TODO: Should this throw the exception, or return some default value?
            throw new IllegalArgumentException("No load profile for day " +
                                               day + " hour " + hour);
        }

        if (hourlyLoadProfileByLSE.length != hourProfile.length){
            System.err.println(String.format(
                    "Inconstitent number of zones at day: %d, hour: %d. "
                            + "Expected %d zones, found %d zones.", day, hour,
                    hourlyLoadProfileByLSE.length, hourProfile.length));
        }
        System.arraycopy(hourProfile, 0, hourlyLoadProfileByLSE, 0,
                hourlyLoadProfileByLSE.length);
    }

    private void announceHourlyLoads(int h, int d) throws AMESMarketException {
        double[] lseDemand = new double[J];
        //assemble just the demand hour h. The loadProfileByLSE
        //is stored as lse x hour. We just want 1 hour for each lse.
        if(loadProfileByLSE != null)
        for(int l = 0; l < J; l++) {
            lseDemand[l] = loadProfileByLSE[l][h];
        }
        ames.announceStatusEvent(
                new SimulationStatusListener.StatusEvent(
                        SimulationStatusListener.StatusEvent.UPDATE_LSE_DEMAND,
                        lseDemand, this));
        //update the actual loads
        double[] statusLoads = new double[J];
        getActualLoadProfile(h, d, statusLoads);
        ames.announceStatusEvent(
                new SimulationStatusListener.StatusEvent(
                        SimulationStatusListener.StatusEvent.UPDATE_RT_LOAD,
                        statusLoads, this));
    }

    // Get and set method

    public double[][] getSupplyOfferByGen() {
        return supplyOfferByGen;
    }
    public double[][] getLoadProfileByLSE() {
        return loadProfileByLSE;
    }
    public double[][] getNextDayLoadProfileByLSE() {
        return nextDayLoadProfileByLSE;
    }
    public double[][][] getDemandBidByLSE() {
        return demandBidByLSE;
    }

    public int[][] getDemandHybridByLSE() {
        return demandHybridByLSE;
    }

    public double[] getHourlyLMP(int h){
        if (dailylmp != null) {
            return dailylmp[h];
        } else {
            return null;
        }

    }

    public List<CommitmentDecision> getGenSchedule(){
        return genSchedule;
    }

    public int[][] getGenScheduleAsArray() {
        return commDecisionListToArray(genSchedule);
    }

    public List<CommitmentDecision> getGenScheduleRT(){
        return genScheduleRT;
    }

    public int[][] getGenScheduleRTAsArray() {
        return commDecisionListToArray(genScheduleRT);
    }

    /**
     * Convert the list of commitment decisions to the 'older' style
     * of int[][] decisions.
     * @param cd
     * @return
     */
    private int[][] commDecisionListToArray(List<CommitmentDecision> cd) {
        if(cd == null){return new int[0][0];}

        int[][] gs = new int[cd.size()][];
        for(CommitmentDecision c : cd){
            gs[c.generatorIdx] = c.commitmentDecisions;
        }

        return gs;
    }

    
    public int getScenarios(){
      noOfScenarios=3;
      return noOfScenarios;
    }
    
    public double[][] getSupplyOfferByGenRT() {
        return supplyOfferRT;
    }

    public double[][] getPriceSensitiveDispatchRT() {
        return dailyPriceSensitiveDispatch;
    }

    public int[][] getRealTimeDemandHybridByLSE() {
        return demandHybridByLSERealTime;
    }

    public DAMarket getDAMarket() {
        return dam;
    }
    public SRMarket getSRMarket() {
        return srm;
    }
    public RTMarket getRTMarket() {
        return rtm;
    }
    public FTRMarket getFTRMarket() {
        return ftrm;
    }
    public SCED getSCED() {
        return sced;
    }

    public double[][] getDailyLMP() {
        //TODO: Is this the rt or dayahead value?
        return buc.getDailyLMP();
    }
    
    public double[] getHourlyBranchFlow(int hour) {
        return accessRow(dailyBranchFlow, hour, false);
    }

    /**
     * 
     * @param hour
     * @param copy
     * @return
     */
    public double[] getHourlyRTBranchFlow(int hour, boolean copy) {
        return accessRow(dailyRTbranchflow, hour, copy);
    }

    public double[] getHourlyCommitment(int hour) {
        return accessRow(dailycommitment, hour, false);
    }

    /**
     * 
     * @param hour
     * @param copy
     * @return
     */
    public double[] getHourlyRTCommitment(int hour, boolean copy) {
        return accessRow(dailyRTcommitment, hour, copy);
    }

    /**
     *
     * @param hour
     * @return null, or the costs for each genco.
     */
    public double[][] getHourlyRTCosts(int hour) {
        double[][] costs = new double[ames.getNumGenAgents()][];
        if(dailyRTProductionCost == null || dailyRTShutdownCost == null || dailyRTStartupCost == null)
            return null;

        double[] up, down, prod;
        up = accessRow(dailyRTStartupCost, hour, false);
        down = accessRow(dailyRTShutdownCost, hour, false);
        prod = accessRow(dailyRTProductionCost, hour, false);
        int numCosts = dailyRTProductionCost[hour].length;
        for(int i = 0; i < numCosts ; i++){
            costs[i] = new double[3];
            costs[i][0] = up[i];
            costs[i][1] = prod[i];
            costs[i][2] = down[i];
        }

        return costs;
    }

    /**
     * Helper method to access a single row of a matrix style array of arrays.
     * @param v matrix to access
     * @param i row number
     * @param copy Whether or not to create a copy of the row
     * @return 
     */
    private final double[] accessRow(double[][] v, int i, boolean copy) {
        double[] rbf = null;

        if (v != null) {
            if (copy) {
                if(v[i] != null){
                    rbf = Arrays.copyOf(v[i],v[i].length);
                }
            } else {
                rbf = v[i];
            }
        }

        return rbf;
    }

    public int getHourlyHasSolution(int hour) {
        int[] sols = buc.getHasSolution();
        if (sols != null) {
            return sols[hour];
        } else {
            return 0;
        }
    }
}
