package amesmarket.extern.psst;
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

public class PSSTSCUCSolver implements SCUC {
    private List<CommitmentDecision> genSchedule;
    private final AMESMarket ames;
    private final ISO iso;

    private final int numHours; // numGenAgents;

    public PSSTSCUCSolver(ISO independentSystemOperator, AMESMarket model) {
        ames = model;
        iso = independentSystemOperator;
        numHours = ames.NUM_HOURS_PER_DAY;

    }


    /* (non-Javadoc)
	 * @see amesmarket.extern.psst.PSSTSCUCSolver#calcSchedule(int)
	 */
    @Override
	public void calcSchedule(int day) throws IOException, AMESMarketException, BadDataFileFormatException {
        genSchedule = new ArrayList<CommitmentDecision>();
        for (GenAgent gc : ames.getGenAgentList()) {

            int[] schedule = new int[numHours];
            int i=0;
            while (i < numHours) {
                schedule[i]=1;
                i++;
            }

            genSchedule.add(new CommitmentDecision(gc.getID(), gc.getIndex(), schedule));
        }

    }

    /* (non-Javadoc)
	 * @see amesmarket.extern.psst.PSSTSCUCSolver#getSchedule()
	 */
    @Override
	public List<CommitmentDecision> getSchedule() {

        return genSchedule;
    }



}
