package amesmarket.extern.psst;

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
import amesmarket.ISO;
import amesmarket.SCED;
import amesmarket.Support;
import amesmarket.TransGrid;
import amesmarket.filereaders.AbstractConfigFileReader;
import amesmarket.filereaders.BadDataFileFormatException;


public class PSSTSCEDSolver implements SCED {

    private final AMESMarket ames;
    private final ISO iso;

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


    public PSSTSCEDSolver(ISO independentSystemOperator, AMESMarket model) {
        ames = model;
        iso = independentSystemOperator;
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




    }


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



}
