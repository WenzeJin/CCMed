/**
 * The Core of this CC Tester.
 * All tests will be performed by 5 steps:
 * 1. Preprocess of source test case inputs.    -> source test cases
 *      This step include read constraints from xml, generate random data input, enumerate bfuncs
 * 2. Testing with source test cases.           -> source test cases with result
 *      This step will be performed by SourceExecutor.
 * 3. Generate follow-up test cases with MRs.   -> follow-up test cases with assertions
 *      This step will be performed by implements of different MRs.
 *      All of them will implement interface `MR`. Which takes a list of source test cases (with result)
 *      and generates a list of follow-up test cases.
 * 4. Testing with follow-up test cases.        -> follow-up test cases with result
 *      This step will also be performed by FollowExecutor.
 *      Analyse results of follow-up test cases, with `MR` class.
 *      If assertion failed, save records in TestCase. If interrupt-when-failed enabled, stop testing.
 * 5. Analyse result and generate report.       -> report. (Optional: human-readable)
 * Author: Wenze Jin
 */

package com.wenzejin.core;

public class TesterCore {

    public TesterCore() {

    }

}
