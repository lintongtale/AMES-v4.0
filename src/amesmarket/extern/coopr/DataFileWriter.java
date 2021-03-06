/*
 * FIXME: LICENSE
 */
package amesmarket.extern.coopr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import amesmarket.AMESMarket;
import amesmarket.AMESMarketException;
import amesmarket.GenAgent;
import amesmarket.ISO;
import amesmarket.LSEAgent;
import amesmarket.extern.common.CommitmentDecision;

/**
 *
 * Write the data files used to transfer infermation over to the various coopr
 * programs.
 *
 * @author Sean L. Mooney
 *
 */
public class DataFileWriter {

    //TODO-XX: make sure everywhere that writes data is using the same
    //object, instead of hard coded paths.
    private final File scenDir = new File("SCUCresources/ScenarioData");

    public void writeScenarioStructures(int noOfScenarios, double[] scenProb) throws AMESMarketException, IOException {

        String nodeBase = "ScenNode";
        String scenBase = "Scen";
        File fileObj = new File(scenDir, "ScenarioStructure.dat");

        if ( !ensureFileParentExists(fileObj) ) {
            throw new AMESMarketException("Could not create the directory for " + fileObj.getPath());
        }

        FileWriter ScenBFileWriter = new FileWriter(fileObj);
        BufferedWriter ScenBufferWriter = new BufferedWriter(ScenBFileWriter);

        try {

            ScenBufferWriter.write("# Written by AMES");
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "MM/dd/yyyy HH:mm:ss\n\n");
            Date date = new Date();
            ScenBufferWriter.write(dateFormat.format(date));

            ScenBufferWriter.write("set Stages := FirstStage SecondStage ;\n");
            ScenBufferWriter.write("\n");

            ScenBufferWriter.write("set Nodes :=\n");
            ScenBufferWriter.write("RootNode\n");

            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(nodeBase + (i + 1) + "\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter.write("param NodeStage :=\n");
            ScenBufferWriter.write("RootNode FirstStage\n");

            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(nodeBase + (i + 1) + " SecondStage\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter.write("set Children[RootNode] :=\n");
            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(nodeBase + (i + 1) + "\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter.write("param ConditionalProbability :=\n");
            ScenBufferWriter.write("RootNode 1.0\n");

            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(nodeBase + (i + 1) + " " + scenProb[i]
                        + "\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter.write("set Scenarios :=\n");
            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(scenBase + (i + 1) + "\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter.write("param ScenarioLeafNode :=\n");
            for (int i = 0; i < noOfScenarios; i++)
                ScenBufferWriter.write(scenBase + (i + 1) + " " + nodeBase
                        + (i + 1) + "\n");
            ScenBufferWriter.write(";\n\n");

            ScenBufferWriter
                    .write("set StageVariables[FirstStage] :=  UnitOn[*,*] ;\n");
            ScenBufferWriter
                    .write("set StageVariables[SecondStage] :=  PowerGenerated[*,*]\n");
            ScenBufferWriter
                    .write("                                    MaximumPowerAvailable[*,*]\n");
            ScenBufferWriter
                    .write("                                    Angle[*,*]\n");
            ScenBufferWriter
                    .write("                                    LoadGenerateMismatch[*,*] ;\n");

            ScenBufferWriter.write("\n");

            ScenBufferWriter
                    .write("param StageCostVariable := FirstStage  StageCost[FirstStage]\n");
            ScenBufferWriter
                    .write("                           SecondStage StageCost[SecondStage] ;\n");

            ScenBufferWriter.write("\n");
            ScenBufferWriter.write("param ScenarioBasedData := True ;\n");

            ScenBufferWriter.close();
        } finally {
            if( ScenBFileWriter != null ) {
                ScenBFileWriter.close();
            }
        }


    }

    /**
     *
     * @param fileObj
     * @param ames
     * @param day
     * @param LoadProfileLSE
     * @param numIntervalsInSim 
     * @throws AMESMarketException
     */
    public void writeScenDatFile(File fileObj, AMESMarket ames,  int day, double [][] LoadProfileLSE, int numIntervalsInSim) throws AMESMarketException {
        //set up all the elements we need.
        final int numHoursPerDay = ames.NUM_HOURS_PER_DAY;
//        final int numIntervalsInSim = ames.NUM_HOURS_PER_DAY_UC;
        final ISO iso = ames.getISO();
        final int numNodes = ames.getNumNodes();
        final double baseS = ames.getBaseS();
        final int numGenAgents = ames.getNumGenAgents();
        final int numLSEAgents = ames.getNumLSEAgents();

        final double[][] branchIndex;
        final double[][] numBranchData;
        final double reserveRequirements;

        if ( !ensureFileParentExists(fileObj) ) {
            throw new AMESMarketException("Could not create the directory for " + fileObj.getPath());
        }

        numBranchData=ames.getBranchData();
        reserveRequirements=ames.getReserveRequirements();
        branchIndex=ames.getTransGrid().getBranchIndex();

        //Now that we have all the parameters. Write it out.
        try {



            BufferedWriter refBufferWriter = new BufferedWriter(new FileWriter(fileObj));

            refBufferWriter.write("# Written by AMES per unit ");
            SimpleDateFormat dateFormat = new SimpleDateFormat(
                    "MM/dd/yyyy HH:mm:ss\n\n");
            Date date = new Date();
            refBufferWriter.write(dateFormat.format(date));

            refBufferWriter.write("set StageSet := FirstStage SecondStage ;\n");
            refBufferWriter.write("\n");

            refBufferWriter
                    .write("set CommitmentTimeInStage[FirstStage] := 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 ;\n");
            refBufferWriter
                    .write("set CommitmentTimeInStage[SecondStage] := ;\n\n");

            refBufferWriter
                    .write("set GenerationTimeInStage[FirstStage] := ;\n");
            refBufferWriter
                    .write("set GenerationTimeInStage[SecondStage] := 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 ;\n\n");

            refBufferWriter.write("set Buses := ");


            for (int i = 0; i < numNodes; i++) {
                refBufferWriter.write("Bus" + (i + 1) + " ");
            }
            refBufferWriter.write(";\n\n");

            refBufferWriter.write("set TransmissionLines :=\n");

            for (int i = 0; i < branchIndex.length; i++)
                refBufferWriter.write("Bus" + (int) branchIndex[i][0] + " Bus"
                        + (int) branchIndex[i][1] + "\n");

            refBufferWriter.write(";\n\n");

            refBufferWriter.write("param NumTransmissionLines := "
                    + branchIndex.length + " ;\n\n");
            
            refBufferWriter
                    .write("param: BusFrom BusTo ThermalLimit Reactance :=\n");

            for (int i = 0; i < branchIndex.length; i++)
                refBufferWriter.write((i + 1) + " Bus"
                        + (int) numBranchData[i][0] + " Bus"
                        + (int) numBranchData[i][1] + " " + numBranchData[i][2]
                        + " " + numBranchData[i][3] + "\n");

            refBufferWriter.write(";\n\n");

            refBufferWriter.write("set ThermalGenerators := ");

            for (GenAgent gc : ames.getGenAgentList())
                refBufferWriter.write(gc.getID() + " ");

            refBufferWriter.write(";\n\n");

            GenAgent[][] genNodeBusTable = new GenAgent[numNodes][numGenAgents];
            for (int i = 0; i < numNodes; i++) {
                for (int j = 0; j < numGenAgents; j++) {
                    GenAgent gen = (GenAgent) ames.getGenAgentList().get(j);
                    //System.out.println(gen.getAtNode());

                    if (gen.getAtNode() - 1 == i)
                        genNodeBusTable[i][j] = gen;
                    else
                        genNodeBusTable[i][j] = null;
                }
            }

            for (int i = 0; i < numNodes; i++) {
                refBufferWriter.write("set ThermalGeneratorsAtBus[Bus"
                        + (i + 1) + "] := ");

                for (int j = 0; j < numGenAgents; j++) {
                    if (genNodeBusTable[i][j] != null)
                        refBufferWriter.write(genNodeBusTable[i][j].getID() + " ");

                }

                refBufferWriter.write(" ;\n");
            }

            refBufferWriter.write("\nparam NumTimePeriods := " + numIntervalsInSim
                    + " ;\n\n");

            refBufferWriter
                    .write("param: PowerGeneratedT0 UnitOnT0State MinimumPowerOutput MaximumPowerOutput MinimumUpTime MinimumDownTime NominalRampUpLimit NominalRampDownLimit StartupRampLimit ShutdownRampLimit ColdStartCost HotStartCost ShutdownCostCoefficient :=\n");


            for(GenAgent ga : ames.getGenAgentList()) {
                refBufferWriter.write(genAgentToSCUCDesc(ga, day, baseS));
                refBufferWriter.write("\n");
            }

            refBufferWriter.write(" ;\n");

            refBufferWriter.write("param: Demand :=\n");

            for (int i = 0; i < numLSEAgents; i++) {
                LSEAgent lse = ames.getLSEAgentList().get(i);
                int lseNode = lse.getAtNode();

                for (int h = 0; h < numIntervalsInSim; h++) {
                    refBufferWriter.write("Bus" + lseNode + " " + (h + 1) + " "
                            + LoadProfileLSE[i][h] / baseS + "\n");
                }

                refBufferWriter.write("\n");
            }

            refBufferWriter.write("; \n");

            refBufferWriter.write("param: ReserveRequirement := \n");

            for (int h = 0; h < numIntervalsInSim; h++) {
                refBufferWriter.write((h + 1) + " " + reserveRequirements
                        / baseS + "\n");
            }

            refBufferWriter.write("; \n");

            refBufferWriter
                    .write("param: ProductionCostA0 ProductionCostA1 ProductionCostA2 :=\n");

            double[][] supplyOfferByGen = iso.getSupplyOfferByGen();

            final ArrayList<GenAgent> genagents = ames.getGenAgentList();
            for (int i = 0; i < numGenAgents; i++){
                GenAgent ga = genagents.get(i);
                refBufferWriter.write(
                        ga.getID() + " " + //ga.getSupplyOffer()[0] + " "
                        + ga.getNoLoadCost() + " " + //FIXME: Is this supposed be part of the getSupplyOffer?
                        + supplyOfferByGen[i][0] * baseS + " "
                        + supplyOfferByGen[i][1] * baseS
                        * baseS + " " + "\n");
            }

            refBufferWriter.write("; \n");

            refBufferWriter.close();
        } catch (IOException e){
            throw new AMESMarketException("Unable to write the reference model.", e);
        }
    }

    /**
     * Get a string to write into the SCUC input file describing the genco.
     * @param ga
     * @param baseS
     * @return a string with all of the parameters, or an empty string if the ga parameter is null.
     */
    private String genAgentToSCUCDesc(GenAgent ga, int day, double baseS) {
        if(ga == null) return "";

        //do all the conversions
        double powerT0          = ga.getPowerT0(day - 1) / baseS;
        double capMin           = ga.getCapacityMin() / baseS;
        double capMax           = ga.getCapacityMax() / baseS;
        double nominalUp        = ga.getNominalRampUpLim() / baseS;
        double nominalDown      = ga.getNominalRampDownLim() / baseS;
        double startupramplimit = ga.getStartupRampLim() / baseS ;
        double shutdownramplimit= ga.getShutdownRampLim() / baseS ;
        double coldstartupcost  = ga.getColdStartUpCost() ;
        double hotstartupcost  = ga.getHotStartUpCost()  ;
        double shutdowncost     = ga.getShutDownCost()  ;

        //some rounding checks
        if (powerT0 < capMin) {
            System.err.println("Warning: " + ga.getID() + " PowerT0 value of "
                    + powerT0 + " is less than capMin of " + capMin +
                    ". Adjusting to " + capMin
                    );
            powerT0 = capMin;
        } else if (powerT0 > capMax) {
            System.err.println("Warning: " + ga.getID() + " PowerT0 value of "
                    + powerT0 + " excedes the capMax value of " + capMax +
                    ". Adjusting to " + capMax
                    );
            powerT0 = capMax;
        }

        return String.format(
                // 1     2          3       4       5       6       7       8               9               10          11
                //Name, powerTO, On/OffT0, MinPow, MaxPow, MinUp, MinDown, NominalRampUP, NominalRampDown, StartupLim, ShutdownLim, ColdStartupCost, HotStartupCost, ShutDownCost
                "%1$s %2$f %3$d %4$f %5$f %6$d %7$d %8$f %9$f %10$f %11$f %12$f %13$f %14$f" //TODO-XX Decimal precision.
                , ga.getID() //1
                , powerT0 //2
                , ga.getUnitOnT0State(day - 1) //3
                , capMin //4
                , capMax//5
                , ga.getMinUpTime()           //6
                , ga.getMinDownTime()         //7
                , nominalUp //8
                , nominalDown//9
                , startupramplimit //10
                , shutdownramplimit //11
                , coldstartupcost //12
                , hotstartupcost //13
                , shutdowncost //14
                );
    }

    /**
     * Write out the generator commitments for the sced.
     * @param gencoCommitments not null
     * @param ucVectorFile file to write the information into.
     * @throws AMESMarketException
     * @throws IllegalArgumentException if gencoCommitments is null.
     */
    public void writeGenCommitments(List<CommitmentDecision> gencoCommitments, File ucVectorFile) throws AMESMarketException {
        if (gencoCommitments == null)
            throw new IllegalArgumentException();

        if ( !ensureFileParentExists(ucVectorFile) ) {
            throw new AMESMarketException("Could not create the directory for " + ucVectorFile.getPath());
        }

        PrintWriter out = null;
        try {
            out = new PrintWriter(new FileWriter(ucVectorFile));

            final String eol = System.getProperty("line.separator");
            final String indent = "\t";
            //strings for whether or not the unit is committed.
            final String ucOn = "1";
            final String ucOff = "0";

            for (CommitmentDecision cd : gencoCommitments) {
                out.println(cd.generatorName); //FIXME: ?BUG?
                StringBuilder sb = new StringBuilder();

                int[] commitmentVector = cd.commitmentDecisions;
                //Boolean[] commitmentVector = gencoCommitments.get(g);
                if(commitmentVector == null) { //yes, I'm being very cautious.
                    System.err.println("[Warning External SCED] No commit vector for " + cd.generatorName);
                    continue;
                }

                for(int b : commitmentVector) {
                    sb.append(indent);
                    sb.append((b == 1 ? ucOn : ucOff)); //convert boolean to the format the external SCED expects.
                    sb.append(eol);
                }
                out.print(sb.toString());
            }

            out.close();
        } catch (IOException e) {
            throw new AMESMarketException("Unable to write the generator commitment schedule.", e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private boolean ensureFileParentExists(File f) {
        File parent = f.getParentFile();
        if( parent == null ) return true; //no parent file. nothing to be done
        else if ( parent.exists() ) return true; //nothing to be done.
        else {
            parent.mkdirs(); //try and make it. return whether or not it exists.
            return parent.exists();
        }
    }
}
