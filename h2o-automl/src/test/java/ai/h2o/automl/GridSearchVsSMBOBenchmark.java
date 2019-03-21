package ai.h2o.automl;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.ModelBuilder;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.fvec.Frame;

import java.util.PriorityQueue;
import java.util.Random;

import static ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy.Evaluated;

public class GridSearchVsSMBOBenchmark extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private Frame getPreparedTitanicFrame(String responseColumnName) {
    Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
    fr.remove(new String[]{"name", "ticket", "boat", "body"});
    asFactor(fr, responseColumnName);
    return fr;
  }
  
  int topListSize = 3;

  private GridSearchTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> [] findBestTargetEncodingParams(Frame fr, String responseColumnName, int numberOfIterations, long seed) {

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    GridSearchTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new GridSearchTEParamsSelectionStrategy(fr, numberOfIterations, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(fr, responseColumnName, seed);
    mb.init(false);
    PriorityQueue<Evaluated<TargetEncodingParams>> evaluatedQueue = gridSearchTEParamsSelectionStrategy.getEvaluatedQueue();
    Evaluated<TargetEncodingParams> bestParamsWithEvaluation = gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);

    Evaluated<TargetEncodingParams> [] leaders = new Evaluated[topListSize];
    for (int i = 0; i < topListSize; i++) {
      leaders[i] = gridSearchTEParamsSelectionStrategy.getEvaluatedQueue().poll();
    }
    assert bestParamsWithEvaluation.getScore() == leaders[0].getScore();
    return leaders;
  }

  private SMBOTEParamsSelectionStrategy.Evaluated<TargetEncodingParams> findBestTargetEncodingParamsWithSMBO(Frame fr, String responseColumnName, double earlyStoppingRatio, long seed) {

    asFactor(fr, responseColumnName);

    TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
    String[] columnsToEncode = strategy.getColumnsToEncode();

    SMBOTEParamsSelectionStrategy gridSearchTEParamsSelectionStrategy =
            new SMBOTEParamsSelectionStrategy(fr, earlyStoppingRatio, responseColumnName, columnsToEncode, true, seed);

    gridSearchTEParamsSelectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
    ModelBuilder mb = TargetEncodingTestFixtures.modelBuilderWithValidFrameFixture(fr, responseColumnName, seed);
    mb.init(false);
    return gridSearchTEParamsSelectionStrategy.getBestParamsWithEvaluation(mb);
  }
  
  @Test
  public void benchmark() {
    int numberOfSearchIterations = 189; //377; 
    String responseColumnName = "survived";
    Frame frForRGS = null;
    Frame frameForSMBO = null;
    Random generator = new Random();
    
    int successCount = 0;
    int averageIndexWhenBestParamsWasFoundSMBO = 0;
    double averageTimeRGS = 0;
    double averageTimeSMBO = 0;

    int numberOfRuns = 10;
    for (int seedAttempt = 0; seedAttempt < numberOfRuns; seedAttempt++) {
      long seed = generator.nextLong();
      try {
        long start1 = System.currentTimeMillis();
        frForRGS = getPreparedTitanicFrame("survived");
        Evaluated<TargetEncodingParams> [] bestParamsFromRGS = findBestTargetEncodingParams(frForRGS, responseColumnName, numberOfSearchIterations, seed);
        long timeWithRGS = System.currentTimeMillis() - start1;

        long start2 = System.currentTimeMillis();
        frameForSMBO = getPreparedTitanicFrame("survived");
//        Evaluated<TargetEncodingParams> bestParamsFromSMBO = findBestTargetEncodingParamsWithSMBO(frameForSMBO, responseColumnName, 1, seed);
        Evaluated<TargetEncodingParams> bestParamsFromSMBO = findBestTargetEncodingParamsWithSMBO(frameForSMBO, responseColumnName, 0.15, seed);
        long timeWithSMBO = System.currentTimeMillis() - start2;
        System.out.println("Time with GRS: " + timeWithRGS);
        System.out.println("Time with SMBO: " + timeWithSMBO);

        averageTimeRGS += timeWithRGS;
        averageTimeSMBO += timeWithSMBO;

        System.out.println("bestParamsFromSMBO.getScore(): " + bestParamsFromSMBO.getScore());
        System.out.println("bestParamsFromRGS.getScore(): " + bestParamsFromRGS[0].getScore());

        // If SMBO prediction is in top `topListSize` then it is considered as a success
        if(bestParamsFromSMBO.getScore() >= bestParamsFromRGS[topListSize - 1].getScore()) successCount++;

        System.out.println("Index of best found params for " + seedAttempt + "st attempt : " + bestParamsFromSMBO.getIndex() );

        averageIndexWhenBestParamsWasFoundSMBO += bestParamsFromSMBO.getIndex();
//        assertTrue(bestParamsFromSMBO.getScore() == bestParamsFromRGS.getScore());
//        assertTrue(timeWithSMBO <= timeWithRGS);

      } finally {
        frForRGS.delete();
        frameForSMBO.delete();
      }
    }
    System.out.println("Number of times SBMO beat RGS is " + successCount + " out of total " + numberOfRuns + " runs. Probability: " + (double)successCount / numberOfRuns);
    System.out.println("Average index of best found(locally) params: " + (double) averageIndexWhenBestParamsWasFoundSMBO / numberOfRuns );
    System.out.println("Average time RGS: " + averageTimeRGS / numberOfRuns );
    System.out.println("Average time SMBO: " + averageTimeSMBO / numberOfRuns );
    H2O.STORE.clear();
  }

}
