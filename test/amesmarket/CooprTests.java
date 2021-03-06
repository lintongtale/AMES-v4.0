//FIXME LICENCE
package amesmarket;

import static org.junit.Assert.*;
import java.io.File;

import org.junit.Test;

import amesmarket.extern.coopr.CooprConfig;

/**
 * Tests relating to the external interface with coopr.
 * @author Sean L. Mooney
 *
 */
public class CooprTests {

    /**
     * A test to make sure the external call to run the scuc
     * is still working.
     */
    @Test
    public void regtestCreateSCUCCall(){
        final File referenceModelDir=new File("SCUCresources/Models");
        final File scenarioModelDir=new File("SCUCresources/ScenarioData");
        final String[] expcmd={"runef","-m",referenceModelDir.getAbsolutePath(),"-i",scenarioModelDir.getAbsolutePath(),"--solve","--solution-writer=runefsolprint"};

        CooprConfig testConfig = CooprConfig.createStochasticCoopr(referenceModelDir, scenarioModelDir, "runefsolprint");
        String[] cooprCmd = testConfig.getExecCmd();

        assertArrayEquals(expcmd, cooprCmd);
    }
}
