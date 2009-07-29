package tlc2.output;

import tlc2.TLCGlobals;
import util.DebugPrinter;
import util.Set;
import util.ToolIO;

/**
 * This class is used in the following way to support the replacements of the
 * strings inside of the code. Any kind of String used as a message reported to the user should be replaced with
 * the call of the message printer.
 * <br><br>
 * The main class for message resolution and printing is called {@link MP}
 * The main class for constants is called {@link EC}
 * <br><br>
 * Here is an example for usage of non-parameterized messages. Assume we have code like this:
 * <code>Assert.fail("getLevel called for TheoremNode before levelCheck");</code>
 * which should be replaced by the call of Assert with the error code. First of all we need to add an additional
 * case in the method {@link MP#getMessage(int, int, String[])}. For doing this, a new constant is required. 
 * Since all constants are in the {@link EC} class we add a line: 
 * <br><br> 
 * <code>public final static int GET_LEVEL_THEOREM_BEFORE_LEVEL_CHECK = 4005;</code>
 * <br><br>
 * After the constant is added we add the case to the switch:
 * <br><br><code> 
 * case EC.GET_LEVEL_THEOREM_BEFORE_LEVEL_CHECK:
 *     b.append("getLevel called for TheoremNode before levelCheck");
 *     break;
 * </code>
 * <br><br> 
 * After that, we replace the code Assert.fail("getLevel called for TheoremNode before levelCheck"); by
 * <code>Assert.fail(EC.GET_LEVEL_THEOREM_BEFORE_LEVEL_CHECK)</code>; There will be a warning inside of the 
 * class, that SANYCodes can not be resolve, so we add it to the imported classes. In order to do this quickly, 
 * put the cursor into the line with the error, and hit <key>Ctrl + 1</key>. The opened quick assist will propose to add the import...
 * Another acceleration can be achieved by adding the case first and pointing the non-existent constant. Again the error 
 * will appear in the MessageResolver class and the <key>Ctrl + 1</key> will open the quick assist first proposition will be to add
 * the constant to the {@link EC}. This saves the typing of the name of the constant and the modifier, but you have to put 
 * the value of the constant...
 * <br><br> 
 * Now assume there is another message in the same class: 
 * <br><br>
 * <code>Assert.fail("getLevel called for LabelNode before levelCheck");</code>
 * <br><br>
 * The message is actually the same, but instead of TheoremNode it has LabelNode. In order to keep with it you can add another
 * constant, or re-think the semantics of the actual message. So in this case I would change the semantics and rename the 
 * constant to GET_LEVEL_BEFORE_LEVEL_CHECK. Change the code in the case to:
 * <br><br>
 * <code>case EC.GET_LEVEL_BEFORE_LEVEL_CHECK:
 *     b.append("getLevel called for %1% before levelCheck");
 *     break;
 * </code>
 * <br><br>
 * Please note the %1% in the string. This will be replaced by the first parameter. The call will be:
 * <code>Assert.fail(EC.GET_LEVEL_BEFORE_LEVEL_CHECK, new String[]{"TheoremNode"});</code> and 
 * <code>Assert.fail(EC.GET_LEVEL_BEFORE_LEVEL_CHECK, new String[]{"LabelNode"});</code> respectively. 
 * <br><br>
 * There is also a shortcut for writing this: <code>Assert.fail(EC.GET_LEVEL_BEFORE_LEVEL_CHECK, "LabelNode");</code> which
 * is a shortcut.
 * <br><br>
 * In general, you can insert more %i% into the message and provide the parameters in the String array.  
 * <br><br>
 *         
 * @author Simon Zambrovski
 * @version $Id$
 */
public class MP
{
    private static final String DELIM = "@@";
    
    private static final String[] EMPTY_PARAMS = new String[0];

    private static final int NONE = 0;
    private static final int ERROR = 1;
    private static final int TLCBUG = 2;
    private static final int WARNING = 3;
    private static final int STATE = 4;
    private static MP instance = null;

    private static final String CONFIG_FILE_ERROR = "TLC found an error in the configuration file at line %1%\n";

    private Set warningHistory;

    /**
     * The internal instance
     */
    static
    {
        instance = new MP();
    }

    /**
     * Private constructor to avoid instantiations
     */
    private MP()
    {
        warningHistory = new Set();
    }

    /**
     * Returns the formatted message
     * @param messageClass one of ERROR, TLCBUG
     * @param messageCode see ErrorCodes class
     * @param parameters string of parameters to be replaced in the message
     * @return the formatted message
     */
    private synchronized static String getMessage(int messageClass, int messageCode, String[] parameters)
    {

        if (parameters == null)
        {
            parameters = EMPTY_PARAMS;
        }
        DebugPrinter.print("entering MP.getMessage() with error code " + messageCode + " and " + parameters.length
                + " parameters");

        for (int i = 0; i < parameters.length; i++)
        {
            DebugPrinter.print("param " + i + ": '" + parameters[i] + "'");
        }

        StringBuffer b = new StringBuffer();

        if (TLCGlobals.tool)
        {
            // for the tool we always print the message class
            // and message code
            b.append(DELIM).append("STARTMSG ").append(messageCode).append(":").append(messageClass).append(" ").append(DELIM).append("\n");
        } else
        {
            // depending on message class add different prefix
            switch (messageClass) {
            case ERROR:
                b.append("Error: ");
                break;
            case TLCBUG:
                b.append("TLC Bug: ");
                break;
            case STATE:
                b.append("State ");
                break;
            case WARNING:
                b.append("Warning: ");
                break;
            case NONE:
            default:
                break;
            }
        }
        // fill with different message depending on the error code
        switch (messageCode) {
        case EC.UNIT_TEST:
            b.append("[%1%][%2%]");
            break;
        case EC.SYSTEM_STACK_OVERFLOW:
            b.append("This was a Java StackOverflowError. It was probably the result\n"
                    + "of an incorrect recursive function definition that caused TLC to enter\n"
                    + "an infinite loop when trying to compute the function or its application\n"
                    + "to an element in its putative domain.");
            break;

        case EC.SYSTEM_OUT_OF_MEMORY:
            b.append("Java ran out of memory.  Running Java with a larger memory allocation\n"
                    + "pool (heap) may fix this.  But it won't help if some state has an enormous\n"
                    + "number of successor states, or if TLC must compute the value of a huge set.");
            break;

        case EC.WRONG_COMMANDLINE_PARAMS_TLC:
            b.append("%1%\nUsage: java tlc2.TLC [-option] inputfile");
            break;
        case EC.WRONG_COMMANDLINE_PARAMS_SIMULATOR:
            b.append("%1%\nUsage: java tlc2.Simulator [-option] inputfile");
            break;
            
        case EC.SYSTEM_OUT_OF_MEMORY_TOO_MANY_INIT:
            b.append("Out Of Memory. There are probably too many initial states.");
            break;

        case EC.TLC_PP_FORMATING_VALUE:
            b.append("error while formating %1%\n%2%");
            break;

        case EC.TLC_PP_PARSING_VALUE:
            b.append("error while parsing %1%\n%2%");
            break;

        case EC.TLC_METADIR_EXISTS:
            b.append("TLC writes its files to a directory whose name is generated"
                    + " from the current time.\nThis directory should be %1%"
                    + ", but that directory already exists.\n"
                    + "Trying to run TLC again will probably fix this problem.\n");
            break;
        case EC.TLC_METADIR_CAN_NOT_BE_CREATED:
            b.append("TLC could not make a directory for the disk files" + " it needs to write.\n");
            break;

        case EC.TLC_INITIAL_STATE:
            b.append("%1%\nWhile working on the initial state:\n%2%");
            break;

        case EC.TLC_NESTED_EXPRESSION:
            b.append("The error occurred when TLC was evaluating the nested"
                    + "\nexpressions at the following positions:\n%1%");
            break;

        case EC.TLC_ASSUMPTION_FALSE:
            b.append("Assumption %1% is false.");
            break;
        case EC.TLC_SANY_START:
            b.append("Starting SANY...");
            break;
        case EC.TLC_SANY_END:
            b.append("SANY finished.");
            break;
        case EC.TLC_ASSUMPTION_EVALUATION_ERROR:
            b.append("Evaluating assumption %1% failed.\n%2%");
            break;
        case EC.TLC_STATE_NOT_COMPLETELY_SPECIFIED_INITIAL:
            b.append("State is not completely specified by the " + "initial predicate:\n%2%");
            break;
        case EC.TLC_INVARIANT_VIOLATED_INITIAL:
            b.append("Invariant %1% is violated by the initial state:\n%2%");
            break;
        case EC.TLC_PROPERTY_VIOLATED_INITIAL:
            b.append("Property %1% is violated by the initial state:\n%2%");
            break;

        case EC.TLC_STATE_NOT_COMPLETELY_SPECIFIED_NEXT:
            b.append("Successor state is not completely specified by the" + " next-state action.\n");
            break;
        case EC.TLC_INVARIANT_VIOLATED_BEHAVIOR:
            b.append("Invariant %1% is violated.");
            break;

        case EC.TLC_INVARIANT_EVALUATION_FAILED:
            b.append("Evaluating invariant %1% failed.");
            break;

        case EC.TLC_ACTION_PROPERTY_VIOLATED_BEHAVIOR:
            b.append("Error: Action property %1%" + " is violated.");
            break;

        case EC.TLC_ACTION_PROPERTY_EVALUATION_FAILED:
            b.append("Evaluating action property %1% failed.");
            break;

        case EC.TLC_DEADLOCK_REACHED:
            b.append("Deadlock reached.");
            break;

        case EC.TLC_TEMPORAL_PROPERTY_VIOLATED:
            b.append("Temporal properties were violated.\n"
                    + "The following behaviour constitutes a counter-example:\n");
            break;

        // this is a TLC bug
        case EC.TLC_FAILED_TO_RECOVER_NEXT:
            b.append("Failed to recover the next state from its fingerprint.");
            break;
        // this is a TLC bug
        case EC.TLC_FAILED_TO_RECOVER_INIT:
            b.append("Failed to recover the initial state from its fingerprint.");
            break;

        case EC.TLC_BUG:
            b.append("This is probably a TLC bug(%1%).");
            break;

        case EC.TLC_NO_STATES_SATISFYING_INIT:
            b.append("There is no state satisfying the initial state predicate.");
            break;

        case EC.TLC_STRING_MODULE_NOT_FOUND:
            b.append("This is a TLC bug: TLC could not find its built-in String module.\n");
            break;

        case EC.TLC_BACK_TO_STATE:
            b.append("Back to state %1%.\n");
            break;

        case EC.TLC_ERROR_STATE:
            b.append("The error state is:\n");
            break;

        case EC.TLC_BEHAVIOR_UP_TO_THIS_POINT:
            b.append("The behavior up to this point is:");
            break;

        case EC.TLC_REPORTER_DIED:
            b.append("Progress report thread died.");
            break;

        case EC.SYSTEM_ERROR_READING_POOL:
            b.append("when reading the disk (StatePoolReader.run):\n%1%");
            break;

        case EC.SYSTEM_CHECKPOINT_RECOVERY_CORRUPT:
            b.append("TLC encountered the following error while restarting from a "
                    + "checkpoint;\n the checkpoint file is probably corrupted.\n%1%");
            break;
        case EC.SYSTEM_ERROR_READING_STATES:
            b.append("TLC encountered the following error reading the %1% of unexplored states:\n%2%");
            break;
        case EC.SYSTEM_ERROR_WRITING_STATES:
            b.append("TLC encountered the following error writing the %1% of unexplored states:\n%2%");
            break;

        case EC.SYSTEM_ERROR_WRITING_POOL:
            b.append("when writing the disk (StatePoolWriter.run):\n%1%");
            break;

        case EC.SYSTEM_DISKGRAPH_ACCESS:
            b.append("DiskGraph.toString()");
            break;

        case EC.TLC_AAAAAAA:
            b.append("AAAAAA");
            break;
        case EC.TLC_REGISTRY_INIT_ERROR:
            b.append("TLA+ Registry initialization error. The name %1% is already in use.");
            break;

        /* ************************************************************************ */
        case EC.CHECK_FAILED_TO_CHECK:
            b.append("TLC failed in checking traces.");
            break;

        case EC.CHECK_PARAM_USAGE:
            b.append("Usage: java tlc2.tool.CheckImplFile [-option] inputfile");
            break;

        case EC.CHECK_PARAM_MISSING_TLA_MODULE:
            b.append("Missing input TLA+ module.");
            break;

        case EC.CHECK_PARAM_EXPECT_CONFIG_FILENAME:
            b.append("Expect a file name for -config option.");
            break;

        case EC.CHECK_PARAM_NEED_TO_SPECIFY_CONFIG_DIR:
            b.append("need to specify the metadata directory for recovery.");
            break;

        case EC.CHECK_PARAM_WORKER_NUMBER_REQUIRED:
            b.append("Worker number required. But encountered %1%");
            break;

        case EC.CHECK_PARAM_WORKER_NUMBER_TOO_SMALL:
            b.append("At least one worker is required.");
            break;

        case EC.CHECK_PARAM_WORKER_NUMBER_REQUIRED2:
            b.append("Expect an integer for -workers option.");
            break;

        case EC.CHECK_PARAM_DEPTH_REQUIRED:
            b.append("Depth must be an integer. But encountered %1%");
            break;

        case EC.CHECK_PARAM_DEPTH_REQUIRED2:
            b.append("Expect an integer for -depth option.");
            break;

        case EC.CHECK_PARAM_TRACE_REQUIRED:
            b.append("Expect a filename for -trace option.");
            break;

        case EC.CHECK_PARAM_COVREAGE_REQUIRED:
            b.append("An integer for coverage report interval required. But encountered %1%");
            break;

        case EC.CHECK_PARAM_COVREAGE_REQUIRED2:
            b.append("Coverage report interval required.");
            break;

        case EC.CHECK_PARAM_COVREAGE_TOO_SMALL:
            b.append("Expect a nonnegative integer for -coverage option.");
            break;

        case EC.CHECK_PARAM_UNRECOGNIZED:
            b.append("Unrecognized option: %1%");
            break;
        case EC.CHECK_PARAM_TOO_MANY_INPUT_FILES:
            b.append("More than one input files: %1% and %2%");
            break;

        case EC.CHECK_COULD_NOT_READ_TRACE:
            b.append("TLC could not read in the trace. %1%");
            break;

        case EC.TLC_PARSING_FAILED:
            b.append("Parsing or semantic analysis failed.");
            break;
        case EC.TLC_PARSING_FAILED2:
            b.append("Parsing or semantic analysis failed.%1%");
            break;

        /* ************************************************************************ */
        case EC.TLC_VALUE_ASSERT_FAILED:
            b.append("The first argument of Assert evaluated to FALSE; the second argument was:\n%1%");
            break;

        case EC.TLC_FP_NOT_IN_SET:
            b.append("The fingerprint is not in set.");
            break;
        case EC.TLC_FP_COMPLETED:
            b.append("%1%, work completed. Thank you!");
            break;

        case EC.SYSTEM_INDEX_ERROR:
            b.append("Index error.");
            break;

        case EC.SYSTEM_STREAM_EMPTY:
            b.append("The provided input stream was null, empty or could not be accessed.");
            break;

        case EC.TLC_PARAMETER_MUST_BE_POSTFIX:
            b.append("Parameter must be a postfix operator");
            break;

        case EC.SYSTEM_FILE_NULL:
            b.append("File must be not null");
            break;

        case EC.SYSTEM_INTERRUPTED:
            b.append("Thread has been interrupted.");
            break;

        case EC.TLC_COULD_NOT_DETERMINE_SUBSCRIPT:
            b.append("TLC could not determine if the subscript of the next-state relation contains"
                    + "\nall state variables. Proceed with fingers crossed.");
            break;

        case EC.TLC_SUBSCRIPT_CONTAIN_NO_STATE_VAR:
            b.append("The subscript of the next-state relation specified by the specification"
                    + "\ndoes not seem to contain the state variable %1%");
            break;

        case EC.TLC_WRONG_TUPLE_FIELD_NAME:
            b.append("Tuple field name %1% is not an integer.");
            break;

        case EC.TLC_WRONG_RECORD_FIELD_NAME:
            b.append("Record field name %1% is not a string.");
            break;

        case EC.TLC_UNCHANGED_VARIABLE_CHANGED:
            b.append("The variable %1% was changed while it  is specified as UNCHANGED at\n%1");
            break;

        case EC.TLC_EXCEPT_APPLIED_TO_UNKNOWN_FIELD:
            b.append("The EXCEPT was applied to non-existing fields of the value at\n%1%");
            break;
        /* ************************************************************************ */
        case EC.TLC_MODULE_TLCGET_UNDEFINED:
            b.append("TLCGet(%1%) was undefined.");
            break;

        case EC.TLC_MODULE_OVERFLOW:
            b.append("Overflow when computing %1%");
            break;

        case EC.TLC_MODULE_ARGUMENT_ERROR:
            b.append("The %1% argument of %2% should be a %3%, but instead it is:\n%4%");
            break;

        case EC.TLC_MODULE_ARGUMENT_NOT_IN_DOMAIN:
            b.append("The %1% argument of %2% must be in the domain of its %3% argument:\n%4%\n, but"
                    + " instead it is\n%5%");
            break;

        case EC.TLC_MODULE_DIVISION_BY_ZERO:
            b.append("The second argument of \\div is 0.");
            break;

        case EC.TLC_MODULE_NULL_POWER_NULL:
            b.append("0^0 is undefined.");
            break;

        case EC.TLC_MODULE_APPLYING_TO_WRONG_VALUE:
            b.append("Applying %1% to the following value,\nwhich is not %2%:\n%3%");
            break;

        case EC.TLC_MODULE_COMPARE_VALUE:
            b.append("Attempted to compare %1% with the value\n%2%");
            break;

        case EC.TLC_MODULE_APPLY_EMPTY_SEQ:
            b.append("Attempted to apply %1% to the empty sequence.");
            break;

        case EC.TLC_MODULE_CHECK_MEMBER_OF:
            b.append("Attempted to check if the value:\n%1%\nis an element of %2%.");
            break;

        case EC.TLC_MODULE_BAG_UNION1:
            b.append("Attemtped to apply BagUnion to the following set, whose\nelement is not a bag:\n%1%");
            break;

        case EC.TLC_MODULE_TRANSITIVE_CLOSURE:
            b.append("Attemtped to apply TransitiveClosure to a set containing\nthe following value:\n%1%");
            break;

        case EC.TLC_MODULE_COMPUTING_CARDINALITY:
            b.append("Attemtped to compute cardinality of the value\n%1%");
            break;
        case EC.TLC_MODULE_EVALUATING:
            b.append("Evaluating an expression of the form %1% when s is not a %2%:\n%3%");
            break;

        case EC.TLC_MODULE_VALUE_JAVA_METHOD_OVERRIDE:
            b.append("Attempted to apply the operator overridden by the Java method"
                    + "\n%1%,\nbut it produced the following error:\n%2%");
            break;
        case EC.TLC_LIVE_BEGRAPH_FAILED_TO_CONSTRUCT:
            b.append("BEGraph.GetPath: Failed to construct a path.");
            break;
        case EC.TLC_LIVE_IMPLIED:
            b.append("Implied-temporal checking--satisfiability problem has %1% branches.");
            break;
        case EC.TLC_LIVE_CANNOT_HANDLE_FORMULA:
            b.append("TLC cannot handle the temporal formula %1%");
            break;
        case EC.TLC_LIVE_WRONG_FORMULA_FORMAT:
            b.append("Temporal formulas containing actions must be of forms <>[]A or []<>A.");
            break;
        case EC.TLC_EXCEPTED_VALUE:
            b.append("TLC expected a %1% value, but did not find one. %2%");
            break;

        case EC.SYSTEM_UNABLE_NOT_RENAME_FILE:
            b.append("Unable not rename file during the clean-up.");
            break;

        case EC.SYSTEM_DISK_IO_ERROR_FOR_FILE:
            b.append("Disk I/O error accessing the file for %1%.");
            break;

        case EC.SYSTEM_METADIR_EXISTS:
            b.append("TLC writes its files to a directory whose name is generated from the current "
                    + "time.\nThis directory should be %1%, but that directory already exists.\n"
                    + "Trying to run TLC again will probably fix this problem.");
            break;

        case EC.SYSTEM_METADIR_CREATION_ERROR:
            b.append("TLC could not make a directory %1% for the disk files it needs to write.");
            break;

        case EC.TLC_CHOOSE_ARGUMENTS_WRONG:
            b.append("The arguments to %1% are not appropriate.");
            break;

        case EC.TLC_CHOOSE_UPPER_BOUND:
            b.append("Choose can only deal with numbers up to %1%");
            break;

        case EC.TLC_FP_VALUE_ALREADY_ON_DISK:
            b.append("DiskFPSet.mergeNewEntries: %1% is already on disk.\n");
            break;

        case EC.SYSTEM_UNABLE_TO_OPEN_FILE:
            b.append("Unable to open %1%.\n%2%");
            break;

        case EC.SANY_PARSER_CHECK:
            b.append("TLA+ Parser sanity check");
            break;

        case EC.TLC_ARGUMENT_MISMATCH:
            b.append("Argument mismatch in operator application.%1");
            break;

        case EC.TLC_TOO_MNY_POSSIBLE_STATES:
            b.append("Too many possible next states for the last state in the trace.");
            break;
        case EC.TLC_ERROR_REPLACING_MODULES:
            b.append("Found a Java class for module %1%, but unable to read\n" + "it as a Java class object. %2%");
            break;

        case EC.TLC_STARTING:
            b.append("Starting...");
            break;
        case EC.TLC_FINISHED:
            b.append("Finished.");
            break;
        case EC.TLC_MODE_MC:
            b.append("Running in Model-Checking mode.");
            break;
        case EC.TLC_MODE_SIMU:
            b.append("Running Random Simulation with seed %1%.");
            break;
        case EC.TLC_COMPUTING_INIT:
            b.append("Computing initial states...");
            break;
        case EC.TLC_INIT_GENERATED1:
            b.append("Finished computing initial states: %1% distinct state%2% generated.");
            break;
        case EC.TLC_INIT_GENERATED2:
            b.append("Finished computing initial states: %1% states generated, with %2% of them distinct.");
            break;
        case EC.TLC_INIT_GENERATED3:
            b.append("Finished computing initial states: %1% states generated.\n"
                    + "Because TLC recovers from a previous checkpoint, only %2% of them require further exploration.");
            break;
        case EC.TLC_INIT_GENERATED4:
            b.append("Finished computing initial states: %1% states generated, with %2% of them distinct.");
            break;
        case EC.TLC_CHECKING_TEMPORAL_PROPS:
            b.append("Checking temporal properties for the %1% state space...");
            break;

        case EC.TLC_SUCCESS:
            b.append("Model checking completed. No error has been found.\n"
                    + "  Estimates of the probability that TLC did not check all reachable states\n"
                    + "  because two distinct states had the same fingerprint:\n" + "  calculated (optimistic):  %1%\n"
                    + "  based on the actual fingerprints:  %2%");
            break;
        case EC.TLC_SEARCH_DEPTH:
            b.append("The depth of the complete state graph search is %1%.");
            break;
        case EC.TLC_CHECKPOINT_START:
            b.append("Checkpointing of run %1%");
            break;
        case EC.TLC_CHECKPOINT_END:
            b.append("Checkpointing completed.");
            break;
        case EC.TLC_CHECKPOINT_RECOVER_START:
            b.append("Starting recovery from checkpoint %1%");
            break;
        case EC.TLC_CHECKPOINT_RECOVER_END:
            b.append("Recovery completed. %1% states examined. %2% states on queue.");
            break;
        case EC.TLC_CHECKPOINT_RECOVER_END_DFID:
            b.append("Recovery completed. %1% states examined.");
            break;

        case EC.TLC_STATS:
            b.append("%1% states generated, %2% distinct states found, %3% states left on queue.");
            break;
        case EC.TLC_STATS_DFID:
            b.append("%1% states generated, %2% distinct states found.");
            break;
        case EC.TLC_STATS_SIMU:
            b.append("The number of states generated: %1%\nSimulation using seed %2% and aril %3%");
            break;
        case EC.TLC_PROGRESS_STATS:
            b.append("Progress(%1%): %2% states generated, %3% distinct states found, %4% states left on queue.");
            break;
        case EC.TLC_PROGRESS_START_STATS_DFID:
            b.append("Starting level %1%: %2% states generated, %3% distinct states found.");
            break;
        case EC.TLC_PROGRESS_STATS_DFID:
            b.append("Progress: %1% states generated, %2% distinct states found.");
            break;
        case EC.TLC_PROGRESS_SIMU:
            b.append("Progress: %1% states checked.");
            break;

        case EC.TLC_COVERAGE_START:
            b.append("The coverage statistics :");
            break;
        case EC.TLC_COVERAGE_VALUE:
            b.append("  %1%: %2%");
            break;
            
        case EC.TLC_COVERAGE_END:
            b.append("End of statistics.");
            break;

        /* ************************************************************************ */
        // state printing
        case EC.TLC_STATE_PRINT1:
            b.append("%1%:\n%2%");
            break;
        case EC.TLC_STATE_PRINT2:
            b.append("%1%: %2%\n%3%");
            break;
        case EC.TLC_STATE_PRINT3:
            b.append("%1%: Stuttering");
            break;
            
        /* ************************************************************************ */
        // configuration file errors
        case EC.CFG_MISSING_ID:
            b.append(CONFIG_FILE_ERROR);
            b.append("The keyword %2% was not followed by an identifier.");
            break;

        case EC.CFG_TWICE_KEYWORD:
            b.append(CONFIG_FILE_ERROR);
            b.append("The keyword %2% appeared twice.");
            break;

        case EC.CFG_EXPECT_ID:
            b.append(CONFIG_FILE_ERROR);
            b.append("Expected an identifier after %2%.");
            break;

        case EC.CFG_GENERAL:
            b.append(CONFIG_FILE_ERROR);
            break;

        case EC.CFG_EXPECTED_SYMBOL:
            b.append(CONFIG_FILE_ERROR);
            b.append("It was expecting %2%, but did not find it.");
            break;
        case EC.CFG_ERROR_READING_FILE:
            b.append("TLC encountered the following error when trying to read the configuration file %1%:\n%2%");
            break;
        // end configuration file errors
        /* ************************************************************************ */

        case EC.GENERAL:
            // the general error adapts to the number of parameters that are passed
            for (int i = 0; i < parameters.length; i++)
            {
                b.append("%" + (i + 1) + "%");
            }
            break;
        /* 
         *  no information at all (error code wrong) 
         */
        default:
            b.append("Wrong invocation of TLC error printer. Error code not found.");
            break;
        }

        replaceString(b, parameters);

        if (TLCGlobals.tool)
        {
            // for the tool we always print the message code
            b.append("\n").append(DELIM).append("ENDMSG ").append(messageCode).append(" ").append(DELIM);
        } else
        {

            // post processing
            switch (messageClass) {
            case WARNING:
                b.append("\n(Use the -nowarning option to disable this warning.)");
                break;
            case ERROR:
                if (TLCGlobals.tool)
                {
                    b.append("\n--End Error.");
                }
                break;
            case TLCBUG:
            case NONE:
            default:
                break;
            }
        }
        DebugPrinter.print("Leaving getMessage()");
        return b.toString();
    }

    /**
     * Returns the error  
     * @param errorCode
     */
    public static String getError(int errorCode)
    {
        return getError(errorCode, EMPTY_PARAMS);
    }

    /**
     * Returns the error with one parameter 
     * @param errorCode
     * @param parameter
     */
    public static String getError(int errorCode, String parameter)
    {
        return getError(errorCode, new String[] { parameter });
    }

    /**
     * Returns parameterized error message
     * @param errorCode 
     * @param parameters a list of string parameters to be inserted into the message, by replacing 
     * %i% with the i-th parameter in the array
     */
    public static String getError(int errorCode, String[] parameters)
    {
        return getMessage(ERROR, errorCode, parameters);
    }

    /**
     * Returns the message  
     * @param errorCode
     */
    public static String getMessage(int errorCode)
    {
        return getMessage(errorCode, EMPTY_PARAMS);
    }

    /**
     * Returns the message with one parameter 
     * @param errorCode
     * @param parameter
     */
    public static String getMessage(int errorCode, String parameter)
    {
        return getMessage(errorCode, new String[] { parameter });
    }

    /**
     * Returns parameterized error message
     * @param errorCode 
     * @param parameters a list of string parameters to be inserted into the message, by replacing 
     * %i% with the i-th parameter in the array
     */
    public static String getMessage(int errorCode, String[] parameters)
    {
        return getMessage(NONE, errorCode, parameters);
    }

    /**
     * Returns TLC Bug message
     * @param errorCode
     * @return
     */
    public static String getTLCBug(int errorCode)
    {
        return getMessage(TLCBUG, errorCode, EMPTY_PARAMS);
    }

    /**
     * Prints the error for a given error code
     * @param errorCode
     */
    public static void printError(int errorCode)
    {
        printError(errorCode, EMPTY_PARAMS);
    }

    /**
     * Prints the error with one parameter 
     * @param errorCode
     * @param parameter
     */
    public static void printError(int errorCode, String parameter)
    {
        printError(errorCode, new String[] { parameter });
    }

    /**
     * Prints parameterized error message
     * @param errorCode 
     * @param parameters a list of string parameters to be inserted into the message, by replacing 
     * %i% with the i-th parameter in the array
     */
    public static void printError(int errorCode, String[] parameters)
    {
        // write the output
        DebugPrinter.print("entering printError(int, String[]) with errorCode " + errorCode);
        ToolIO.out.println(getMessage(ERROR, errorCode, parameters));
        DebugPrinter.print("leaving printError(int, String[])");
    }

    /**
     * Prints the error by code and the provided exception
     * @param errorCode 
     * @param cause
     * @param includeStackTrace boolean flag if the stack-trace should be printed
     */
    public static void printError(int errorCode, Throwable cause, boolean includeStackTrace)
    {
        printError(errorCode, cause.getMessage());
        if (includeStackTrace)
        {
            DebugPrinter.print("printing stacktrace in printError(int, Throwable, boolean)");
            cause.printStackTrace(ToolIO.out);
        }
    }

    /**
     * Prints the error by code and reports the exception message 
     * @param errorCode
     * @param cause
     */
    public static void printError(int errorCode, Throwable cause)
    {
        printError(errorCode, cause, true);
    }

    /**
     * Prints the error for a given error code
     * @param errorCode
     */
    public static void printMessage(int errorCode)
    {
        printMessage(errorCode, EMPTY_PARAMS);
    }

    /**
     * Prints the error with one parameter 
     * @param errorCode
     * @param parameter
     */
    public static void printMessage(int errorCode, String parameter)
    {
        printMessage(errorCode, new String[] { parameter });
    }

    /**
     * Prints parameterized error message
     * @param errorCode 
     * @param parameters a list of string parameters to be inserted into the message, by replacing 
     * %i% with the i-th parameter in the array
     */
    public static void printMessage(int errorCode, String[] parameters)
    {
        DebugPrinter.print("entering printMessage(int, String[]) with errorCode " + errorCode);
        // write the output
        ToolIO.out.println(getMessage(NONE, errorCode, parameters));
        DebugPrinter.print("leaving printError(int, String[]) with errorCode ");
    }

    /** 
     * Prints the state
     * @param parameters
     */
    public static void printState(int code, String[] parameters)
    {
        DebugPrinter.print("entering printState(String[])");
        ToolIO.out.println(getMessage(STATE, code, parameters));
        DebugPrinter.print("leaving printState(String[])");
    }
    
    /**
     * Prints parameterized TLC BUG message
     * @param errorCode 
     * @param parameters a list of string parameters to be inserted into the message, by replacing 
     * %i% with the i-th parameter in the array
     */
    public static void printTLCBug(int errorCode, String[] parameters)
    {
        DebugPrinter.print("entering printTLCBug(int, String[]) with errorCode " + errorCode);
        // write the output
        ToolIO.out.println(getMessage(TLCBUG, errorCode, parameters));
        DebugPrinter.print("leaving printTLCBug(int, String[])");
    }

    /**
     * Prints a warning (if the global switch is enabled and it is not a duplicate warning)
     * @param errorCode
     * @param parameters
     */
    public static void printWarning(int errorCode, String[] parameters)
    {
        DebugPrinter.print("entering printWarning(int, String[]) with errorCode " + errorCode);
        // only print warnings if the global warning switch was enabled
        if (TLCGlobals.warn)
        {
            // construct the message
            String message = getMessage(WARNING, errorCode, parameters);
            // if the message has not been printed
            if (instance.warningHistory.put(message) == null)
            {
                // print it
                ToolIO.out.println(message);
            }
        }
        DebugPrinter.print("leaving printWarning(int, String[])");
    }

    public static StringBuffer replaceString(StringBuffer buffer, String[] parameters)
    {
        // replace parameters, if any
        int placeHolderPosition = -1;
        String placeHolder = null;
        // replace all parameters
        for (int i = 0; i < parameters.length; i++)
        {
            placeHolder = "%" + (i + 1) + "%";
            placeHolderPosition = buffer.indexOf(placeHolder);
            if (placeHolderPosition != -1)
            {
                buffer.replace(placeHolderPosition, placeHolderPosition + placeHolder.length(), parameters[i]);
            } else
            {
                // the place holder is not found
                // stop processing
                break;
            }
        }

        return buffer;
    }
}
