package ontologizer;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import ontologizer.calculation.CalculationRegistry;
import ontologizer.statistics.IResampling;
import ontologizer.statistics.TestCorrectionRegistry;

public class OntologizerOptions
{
	private String [] calculations;
	private String [] mtcs;
	private Options options;

	public Options options()
	{
		return options;
	}

	public String [] mtcs()
	{
		return mtcs;
	}

	public String [] calculations()
	{
		return calculations;
	}

	public static OntologizerOptions create()
	{
		/* Build up the calculation string to show it within the help description */
		String calculations[] = CalculationRegistry.getAllRegistered();
		StringBuilder calHelpStrBuilder = new StringBuilder();
		calHelpStrBuilder.append("Specifies the calculation method to use. Possible values are: ");

		for (int i=0;i<calculations.length;i++)
		{
			calHelpStrBuilder.append("\"");
			calHelpStrBuilder.append(calculations[i]);
			calHelpStrBuilder.append("\"");

			/* Add default identifier if it is the default correction */
			if (CalculationRegistry.getDefault() == CalculationRegistry.getCalculationByName(calculations[i]))
					calHelpStrBuilder.append(" (default)");

			calHelpStrBuilder.append(", ");
		}
		calHelpStrBuilder.setLength(calHelpStrBuilder.length()-2); /* remove redundant last two characters */
		String calHelpString = calHelpStrBuilder.toString();

		/* Build up the mtc string to show it within the help description */
		boolean resamplingBasedMTCsExists = false;
		String mtcs[] = TestCorrectionRegistry.getRegisteredCorrections();
		StringBuilder mtcHelpStrBuilder = new StringBuilder();
		mtcHelpStrBuilder.append("Specifies the MTC method to use. Possible values are: ");

		for (int i=0;i<mtcs.length;i++)
		{
			if (TestCorrectionRegistry.getCorrectionByName(mtcs[i]) instanceof IResampling)
				resamplingBasedMTCsExists = true;

			mtcHelpStrBuilder.append("\"");
			mtcHelpStrBuilder.append(mtcs[i]);
			mtcHelpStrBuilder.append("\"");

			/* Add default identifier if it is the default correction */
			if (TestCorrectionRegistry.getDefault() == TestCorrectionRegistry.getCorrectionByName(mtcs[i]))
					mtcHelpStrBuilder.append(" (default)");

			mtcHelpStrBuilder.append(", ");
		}
		mtcHelpStrBuilder.setLength(mtcHelpStrBuilder.length()-2); /* remove redundant last two characters */
		String mtcHelpString = mtcHelpStrBuilder.toString();

		Options options = new Options();

		options.addOption(new Option("h","help",false,"Shows this help"));
		options.addOption(new Option("g","go",true,"File containig GO terminology and structure (.obo format). Required"));
		options.addOption(new Option("a","association",true,"File containing associations from genes to GO terms. Required"));
		options.addOption(new Option("p","population",true,"File containing genes within the population. Required"));
		options.addOption(new Option("s","studyset",true,"File of the study set or a directory containing study set files. Required"));
		options.addOption(new Option("i","ignore",false,"Ignore genes to which no association exist within the calculation."));
		options.addOption(new Option("c","calculation",true,calHelpString));
		options.addOption(new Option("m","mtc",true,mtcHelpString));
		options.addOption(new Option("d","dot",true, "For every study set analysis write out an additional .dot file (GraphViz) containing "+
													 "the graph that is induced by interesting nodes. The optional argument in range between 0 and 1 "+
													 "specifies the threshold used to identify interesting nodes. "+
													 "By appending a GO Term identifier (separated by a comma) the output is restricted to the " +
													 "subgraph originating at this GO term."));
		options.addOption(new Option("n","annotation",false,"Create an additional file per study set which contains the annotations."));
		options.addOption(new Option("f","filter",true,"Filter the gene names by appling rules in a given file (currently only mapping supported)."));
		options.addOption(new Option("o","outdir",true,"Specfies the directory in which the results will be placed."));

		if (resamplingBasedMTCsExists) {
			options.addOption(new Option("r","resamplingsteps", true, "Specifies the number of steps used in resampling based MTCs"));
			options.addOption(new Option("t","sizetolerance", true, "Specifies the percentage at which the actual study set size and " +
					"the size of the resampled study sets are allowed to differ"));
		}
		options.addOption(new Option("v","version",false,"Shows version information and exits"));

		OntologizerOptions opts = new OntologizerOptions();
		opts.calculations = calculations;
		opts.options = options;
		opts.mtcs = mtcs;
		return opts;
	}
}
