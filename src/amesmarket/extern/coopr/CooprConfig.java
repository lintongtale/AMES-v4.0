/*
 * FIXME <LICENCE>
 */
package amesmarket.extern.coopr;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Configuration information for using a coopr program to run some
 * optimization problem.
 *
 * More information about coopr can be found
 * <a href=https://software.sandia.gov/trac/coopr>
 * https://software.sandia.gov/trac/coopr
 * </a>
 *
 * This class is generating commands used to run the SCUC/SCED
 * optimizations.
 *
 * This class is currently defaulted to runef specific commands.
 * TODO-X : Make this more general.
 *
 * @author Sean L. Mooney
 *
 */
public class CooprConfig {
    
//    private final String cooprProg;
//    private final File referenceModelDir;
//    private final File scenarioModelDir;
//    private final String solutionWriter;

    private final String[] args;
    /**
     * Create a stocastic coopr(runef) program.
     * @param cooprProg
     * @param referenceModelDir
     * @param scenarioModelDir
     * @param solutionWriter
     */
    private CooprConfig(String ... cooprProgArgs) {
        args = cooprProgArgs;
    }
    
    /**
     * 
     * @param referenceModelDir
     * @param scenarioModelDir
     * @param solutionWriter
     * @return
     */
    public static CooprConfig createStochasticCoopr(File referenceModelDir, File scenarioModelDir, String solutionWriter) {
        return new CooprConfig(
                "runef" ,
                "-m",referenceModelDir.getAbsolutePath(),
                "-i",scenarioModelDir.getAbsolutePath(),
                "--solve", "--solution-writer="+solutionWriter, "--solver-options=\"threads=2\" "
                );
    }

    /**
     * 
     * @param pyomoSolPrint
     * @param referenceModel
     * @param referenceFile
     * @return
     */
    public static CooprConfig createDeterministicCoopr(File pyomoSolPrint, File referenceModel, File referenceFile) {
        return new CooprConfig("pyomo", 
                "--postprocess",
                pyomoSolPrint.getAbsolutePath(),
                referenceModel.getAbsolutePath(),
                referenceFile.getAbsolutePath(),
                "--solver=cplex", "--solver-options=\"threads=2\"");
    }
    
    /**
     * Get the arguments that will on invoked to start the process.
     * @return
     */
    public String[] getExecCmd() {
        return args;
    }

    /**
     * Start a new process that runs a coopr program.
     * @return
     * @throws IOException
     */
    public Process createCooprProcess() throws IOException{
        ProcessBuilder pb = new ProcessBuilder(getExecCmd());
        setupEnv(pb.environment());
        return pb.start();
    }

    /**
     * Ensure the environment is set up correctly for the coopr call.
     * @param env
     */
    private void setupEnv(Map<String, String> env){
        //check to see if the PYTHONPATH has defined
        String pyPathVar = "PYTHONPATH";
        if(!env.containsKey( pyPathVar )) { //The python path wasn't defined
            String cooprPath = pythonPath();
            //TODO: Delete printing when debugged.
            System.out.println(pyPathVar + " not defined\n" +
                "Adding " + pyPathVar + "=" + cooprPath + " to the env path.");
            env.put(pyPathVar, cooprPath);
        }
    }

    /**
     * @return the path to use as the python path
     */
    public String pythonPath() {
        //TODO: Enable setting this path via a system property or from the constructor
        //create file to the SCUCresources folder to make it easy to get the full path.
        java.io.File scucres = new java.io.File("SCUCresources");
        if(!scucres.exists()) {
            System.err.println(scucres.getAbsolutePath() + " does not exist. SCUC may fail.");
        }
        return scucres.getAbsolutePath();
    }
}
