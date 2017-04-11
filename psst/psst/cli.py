# -*- coding: utf-8 -*-

import os

import click
import pandas as pd

from .utils import read_unit_commitment, read_model
from .model import build_model


@click.group()
@click.version_option('0.1.0', '--version')
def cli():
    pass


@cli.command()
@click.option('--config', default='./config.json', help='Run model')
@click.option('--verbose', default=False, is_flag=True, help='Verbose flag')
def run(**kwargs):
    config = os.path.abspath(kwargs.pop('config', None))

    click.echo('Running model based on configuration in {}'.format(config))
    return None


@cli.command()
@click.option('--uc', default=None, type=click.Path(), help='Path to unit commitment file')
@click.option('--data', default=None, type=click.Path(), help='Path to model data')
@click.option('--output', default='./output.dat', type=click.Path(), help='Path to output file')
def sced(uc, data, output):

    click.echo("Running SCED using PSST")

    # TODO : Fixme
    uc_df = pd.DataFrame(read_unit_commitment(uc.strip("'")))

    c = read_model(data.strip("'"))
    model = build_model(c)
    model.solve(solver='xpress')

    with open(output.strip("'"), 'w') as f:
        f.write("LMP\n")
        for h, r in model.results.lmp.iterrows():
            bn = 1
            for _, lmp in r.iteritems():
                f.write(str(bn) + ' : ' + str(h) +' : ' + str(lmp) +"\n")
                bn = bn + 1
        f.write("END_LMP\n")

        f.write("GenCoResults\n")
        instance = model._model
        for g in instance.Generators.value:
            f.write("%s" % str(g).ljust(8))
            for t in instance.TimePeriods:
                f.write("Hour: {}".format(str(t)))
                f.write("\tPowerGenerated: {}".format(instance.PowerGenerated[g, t]()))
                f.write("\tProductionCost: {}".format(instance.ProductionCost[g, t]()))
                f.write("\tStartupCost: {}".format(instance.StartupCost[g, t]()))
                f.write("\tShutdownCost: {}".format(instance.ShutdownCost[g, t]()))
        f.write("END_GenCoResults\n")
        f.write("VOLTAGE_ANGLES\n")
        for bus in sorted(instance.Buses):
            for t in instance.TimePeriods:
                print >> f, str(bus), str(t), ":", str(instance.Angle[bus, t]())
                #print >>f, "\t %s,  : %6.2f"
        f.write("END_VOLTAGE_ANGLES\n")
        # Write out the Daily LMP
        f.write("DAILY_BRANCH_LMP\n")
        f.write("END_DAILY_BRANCH_LMP\n")
        # Write out the Daily Price Sensitive Demand
        f.write("DAILY_PRICE_SENSITIVE_DEMAND\n")
        f.write("END_DAILY_PRICE_SENSITIVE_DEMAND\n")
        # Write out which hour has a solution
        f.write("HAS_SOLUTION\n")
        h = 0
        max_hour = 24 #FIXME: Hard-coded number of hours.
        while h < max_hour:
            f.write("1\t") #FIXME: Hard-coded every hour has a solution.
            h += 1
        f.write("\nEND_HAS_SOLUTION\n")


if __name__ == "__main__":
    cli()
