package ai.h2o.automl.hpsearch;

import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.Random;

/**
 * Sequential model-based optimisation(SMBO) method is a more general name for Bayesian optimisation method for hyperparameters search. 
 * Bayesian optimisation methods build probability models of the objective functions but in general we can use other approaches/models 
 * that can predict score of the objective function with less time required and with utilisation of the history of scores. 
 */
public abstract class SMBO {

  private Frame _history;
  
  /**
   * Contains mappings of hps to scores under objective function
   * @return
   */
  public Frame history() {
    return _history;
  };

  public boolean hasNoPrior() {
    return _history == null || _history.numRows() == 0;
  };

  public void updatePrior(Frame hyperparametersWithScore){
    _history = TargetEncoderFrameHelper.rBind(_history, hyperparametersWithScore);

    AcquisitionFunction acquisitionFunction = acquisitionFunction();
    if(!acquisitionFunction.isIncumbentColdStartSetupHappened()) {
      Frame bestFromPrior = getBestRowByColumn(history(), "score", true);
      acquisitionFunction.setIncumbent(bestFromPrior.vec("score").at(0));
    } else {
      double newScoreOnOF = hyperparametersWithScore.vec("score").at(0);
      acquisitionFunction.updateIncumbent(newScoreOnOF);
    }
  };

  /**
   *  Should be overwritten by user with given objective function evaluation
   * @return
   */
  //  public abstract double objective(Frame hyperparameters); 
  
  public abstract SurrogateModel surrogateModel();
  
  public abstract AcquisitionFunction acquisitionFunction();
  
  public Frame getNextBestHyperparameters(Frame unexploredHyperspace) {
    
    // 1) evaluate whole searchspace with surrogate model. Model is trained on history.
    Frame evaluatedHyperspace = surrogateModel().evaluate(unexploredHyperspace, history());

    printOutFrameAsTable(evaluatedHyperspace, false, 30);
    
    Vec medians = evaluatedHyperspace.vec("prediction");
    Vec variances = evaluatedHyperspace.vec("variance");
    AcquisitionFunction acquisitionFunction = acquisitionFunction();
    
    Vec afEvaluations = acquisitionFunction.compute(medians, variances);
    evaluatedHyperspace.add("afEvaluations", afEvaluations);

    Frame bestHPsBasedOnAF = getBestRowByColumn(evaluatedHyperspace, "afEvaluations", true);

    //Removing predictions as on the next iteration we will have updated prior and new predictions
    unexploredHyperspace.remove("prediction").remove();
    unexploredHyperspace.remove("variance").remove();
    unexploredHyperspace.remove("afEvaluations").remove();
    printOutFrameAsTable(bestHPsBasedOnAF);
    return bestHPsBasedOnAF;
  };
  
  Frame getBestRowByColumn(Frame fr, String columnName, boolean theBiggerTheBetter) {
    int columnIdx = fr.find(columnName);
    Frame sorted = fr.sort(new int[] {columnIdx}, new int[] {-1});
    printOutFrameAsTable(sorted, false, 30);
    Frame bestHPsBasedOnCriteria = sorted.deepSlice(new long[]{0}, null);
    bestHPsBasedOnCriteria._key = Key.make("best_candidate_" + Key.make());
    DKV.put(bestHPsBasedOnCriteria);
    return bestHPsBasedOnCriteria;
  }

  //TODO for dev. remove
  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}
