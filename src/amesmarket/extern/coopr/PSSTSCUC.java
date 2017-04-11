/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package amesmarket.extern.coopr;
import java.io.*;
import java.util.*;
import java.util.logging.Level;

import amesmarket.AMESMarket;
import amesmarket.AMESMarketException;
import amesmarket.GenAgent;
import amesmarket.ISO;
import amesmarket.LoadCaseControl;
import amesmarket.LoadProfileCollection;
import amesmarket.SCUC;
import amesmarket.Support;
import amesmarket.filereaders.BadDataFileFormatException;
import amesmarket.extern.common.CommitmentDecision;

/**
 *
 * @author Dheepak Krishnamurthy
 */

public class PSSTSCUC implements SCUC {

    /**
     * Whether or not to delete the files created.
     */
    private final boolean deleteFiles;

    private final AMESMarket ames;
    private final ISO iso;
    private List<CommitmentDecision> genSchedule;
    private final int numGenAgents,numLSEAgents, numHours, numIntervals; // numGenAgents;

    private final File python_Input, referenceFile, pyomoSolPrint, runefSolPrint, referenceModelDir,
            scenarioModelDir;


    //TODO-X: make pyomo/cplex user settable, somewhere.
    //Probably an option in the TestCase file.
    /**
     * Name/path to pyomo program.
     */
    private final File pyomoProg;

    /**
     * Configuration for external coopr call. Determines
     * if a deterministic or stochastic scuc is writtern.
     */
    private final PSSTConfig PSSTExt;

    public PSSTSCUC(ISO independentSystemOperator, AMESMarket model) {
        ames = model;
        iso = independentSystemOperator;
        numGenAgents = ames.getNumGenAgents();
        numLSEAgents=ames.getNumLSEAgents();
        numHours = ames.NUM_HOURS_PER_DAY;
        numIntervals = ames.NUM_HOURS_PER_DAY_UC;
        final int scucType = model.getSCUCType();

        //genSchedule=new int[numGenAgents][numHoursPerDay];

        python_Input=new File("xfertoames.dat");
        referenceModelDir=new File("SCUCresources/Models");
        scenarioModelDir=new File("SCUCresources/ScenarioData");
        referenceFile=new File(scenarioModelDir, "ReferenceModel.dat");
        pyomoSolPrint=new File("SCUCresources/pyomosolprint.py");
        runefSolPrint=new File("SCUCresources/runefsolprint.py");
        //referenceModel=new File("SCUCresources/Models/ReferenceModel.py");

        switch(scucType) {
        case SCUC_STOC :
          //Run the External SCUC.
            PSSTExt = PSSTConfig.createStochasticPSST(referenceModelDir, scenarioModelDir, "runefsolprint");
            break;
        case SCUC_DETERM :
            PSSTExt = PSSTConfig.createDeterministicPSST(pyomoSolPrint,
                    new File(referenceModelDir, "ReferenceModel.py"),
                    referenceFile);
            break;
        default :
            throw new IllegalArgumentException("Unknown SCUC type");
        }

        //set up the run paths of pyomo and cplex
        pyomoProg = Support.findExecutableInPath("pyomo");

        deleteFiles = model.isDeleteIntermediateFiles();
    }

    /* (non-Javadoc)
	 * @see amesmarket.extern.coopr.SCUC#calcSchedule(int)
	 */
    @Override
	public void calcSchedule(int day) throws IOException, AMESMarketException, BadDataFileFormatException {

        String strTemp;



        //Write out the configuration for the pyomo model.
        double[][] loadProfileLSE = iso.getLoadProfileByLSE();
        double[][] nextDayLoadProfileLSE;
        double[][] loadProfileLSEALL = null;

        if(day<ames.DAY_MAX){
            nextDayLoadProfileLSE = iso.getNextDayLoadProfileByLSE();
            loadProfileLSEALL = new double[loadProfileLSE.length][loadProfileLSE[0].length + nextDayLoadProfileLSE[0].length];
            for (int i=0; i < loadProfileLSE.length; i++) {
                System.arraycopy(loadProfileLSE[i], 0, loadProfileLSEALL[i], 0, loadProfileLSE[i].length);
                System.arraycopy(nextDayLoadProfileLSE[i], 0, loadProfileLSEALL[i], loadProfileLSE[i].length, nextDayLoadProfileLSE[i].length);
            }
        } else {
            loadProfileLSEALL = new double[loadProfileLSE.length][loadProfileLSE[0].length+loadProfileLSE[0].length];
            for (int i=0; i < loadProfileLSE.length; i++) {
                System.arraycopy(loadProfileLSE[i], 0, loadProfileLSEALL[i], 0, loadProfileLSE[i].length);
            }
        }

        DataFileWriter dfw = new DataFileWriter();

        dfw.writeScenDatFile(referenceFile, ames, day, loadProfileLSEALL, ames.NUM_HOURS_PER_DAY_UC);

        LoadCaseControl loadCaseControl = ames.getLoadScenarioProvider().getLoadCaseControl();

        double[] scenProb=new double[loadCaseControl.getNumLoadScenarios()];
        for (int i=0;i<loadCaseControl.getNumLoadScenarios();i++){
            scenProb[i]=loadCaseControl.getScenarioProbability(day, i+1);
        }
        dfw.writeScenarioStructures(loadCaseControl.getNumLoadScenarios(),scenProb);

        double[][] scenarioLoadProfileLSE=new double[numLSEAgents][numHours];
        double[][] nextDayScenarioLoadProfileLSE=new double[numLSEAgents][numHours];
        double[][] scenarioLoadProfileLSEALL=new double[numLSEAgents][numHours+numHours];

        for (int i = 0; i < loadCaseControl.getNumLoadScenarios(); i++) {
            LoadProfileCollection scenario = loadCaseControl.getLoadScenario(i + 1);

            for (int j = 0; j < numLSEAgents; j++) {
                for (int k = 0; k < numHours; k++) {
                    scenarioLoadProfileLSE[j][k] = scenario.get(day)
                            .getLoadByHour(k)[j];
                }

            }

            if(day<ames.DAY_MAX){

                for (int j = 0; j < numLSEAgents; j++) {
                    for (int k = 0; k < numHours; k++) {
                        nextDayScenarioLoadProfileLSE[j][k] = scenario.get(day+1)
                                .getLoadByHour(k)[j];
                    }

                }

                for (int k=0; k < loadProfileLSE.length; k++) {
                    System.arraycopy(scenarioLoadProfileLSE[k], 0, scenarioLoadProfileLSEALL[k], 0, scenarioLoadProfileLSE[k].length);
                    System.arraycopy(nextDayScenarioLoadProfileLSE[k], 0, scenarioLoadProfileLSEALL[k], scenarioLoadProfileLSE[k].length, nextDayScenarioLoadProfileLSE[k].length);
                }
            } else {
                for (int k=0; k < loadProfileLSE.length; k++) {
                    System.arraycopy(scenarioLoadProfileLSE[k], 0, scenarioLoadProfileLSEALL[k], 0, scenarioLoadProfileLSE[k].length);
                }
            }


            File fileObj = new File("SCUCresources/ScenarioData/Scen" + (i + 1)
                    + ".dat");
            dfw.writeScenDatFile(fileObj, ames, day, scenarioLoadProfileLSEALL, ames.NUM_HOURS_PER_DAY_UC);
        }

        syscall(PSSTExt);


        //Read the data file back in to get the GenCo commitments.
        if (!python_Input.exists()) {
            throw new BadDataFileFormatException(new FileNotFoundException(
                    python_Input.getPath()));
        }

        System.out.println("Reading GenCo schedule from " + python_Input.getPath());
        java.util.Scanner raf = new Scanner(python_Input);

        //Read the results from the external scuc back in.
        AMESMarket.LOGGER.log(Level.FINER, "Reading GenCo schedule.");
        genSchedule = new ArrayList<CommitmentDecision>();
        for (int j=0;j<numGenAgents;j++)
        {
            int[] schedule = new int[numHours];
            int i=0;
            String genCoMarker = raf.nextLine().trim();
            GenAgent gc = ames.getGenAgentByName(genCoMarker);
            if (gc == null) {
                throw new BadDataFileFormatException("Unknown GenAgent, "
                        + genCoMarker + ", in SCUC results");
            }

            while (i < numHours)
            {
                strTemp=raf.nextLine();
                if(strTemp == null) {
                    throw new BadDataFileFormatException(
                            "No schedule for " + gc.getID() + " hour " + i);
                }

                int iIndex= strTemp.indexOf(" ");
                //System.out.println(strTemp.substring(iIndex+1,iIndex+2));
                schedule[i]=Integer.parseInt(strTemp.substring(iIndex+1,iIndex+2));

                i++;
            }

            while (i < numIntervals)
            {
                strTemp=raf.nextLine();
                i++;
            }

            genSchedule.add(new CommitmentDecision(gc.getID(), gc.getIndex(), schedule));
        }

        //Sort the collection by array index. Keeps the list
        //in the 'expected' order for ames. The output from the external
        //solver is sorted by name, which means GenCo10 comes after GenCo1
        Collections.sort(genSchedule, new Comparator<CommitmentDecision>() {
            @Override
            public int compare(CommitmentDecision o1, CommitmentDecision o2) {
                if(o1.generatorIdx < o2.generatorIdx) return -1;
                if(o1.generatorIdx == o2.generatorIdx) return 0;
                return 1;
            }
        });

        raf.close();

        cleanup();
        //END Read in GenCo commitments
    }

    public void syscall(PSSTConfig runefConfig) throws IOException {
        Process p = runefConfig.createPSSTProcess();

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
    }


    /**
     * @return The file where the reference model for pyomo was written.
     */
    public File getReferenceModelFile() {
        return referenceFile;
    }

    /* (non-Javadoc)
	 * @see amesmarket.extern.coopr.SCUC#getSchedule()
	 */
    @Override
	public List<CommitmentDecision> getSchedule() {

        return genSchedule;
    }

    private void cleanup() {
        if (deleteFiles) {
            Support.deleteFiles(Arrays.asList(python_Input));
        }
    }
}

