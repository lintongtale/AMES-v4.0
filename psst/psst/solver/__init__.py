
from pyomo.environ import SolverFactory

from .results import PSSTResults

def solve_model(model, solver='glpk', solver_io=None, keepfiles=True, verbose=True, symbolic_solver_labels=True, is_mip=True, mipgap=0.01):
    if solver == 'xpress':
        solver = SolverFactory(solver, solver_io=solver_io, is_mip=is_mip)
    else:
        solver = SolverFactory(solver, solver_io=solver_io)
    model.preprocess()
    if is_mip:
        solver.options['mipgap'] = mipgap

    solver.solve(model, suffixes=['dual'], tee=verbose, keepfiles=keepfiles, symbolic_solver_labels=symbolic_solver_labels)

    return model
