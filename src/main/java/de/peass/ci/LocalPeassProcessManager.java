package de.peass.ci;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.xml.bind.JAXBException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.peass.analysis.changes.ProjectChanges;
import de.peass.ci.helper.HistogramReader;
import de.peass.ci.helper.HistogramValues;
import de.peass.ci.helper.RCAVisualizer;
import de.peass.ci.persistence.TrendFileUtil;
import de.peass.ci.remote.RemoteMeasurer;
import de.peass.ci.remote.RemoteRCA;
import de.peass.config.MeasurementConfiguration;
import de.peass.measurement.analysis.ProjectStatistics;
import de.peass.measurement.rca.CauseSearcherConfig;
import de.peass.measurement.rca.RCAStrategy;
import de.peass.utils.Constants;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class LocalPeassProcessManager {
   private final FilePath workspace;
   private final File localWorkspace;
   private final TaskListener listener;
   private final MeasurementConfiguration configWithRealGitVersions;
   private final EnvVars envVars;

   public LocalPeassProcessManager(final FilePath workspace, final File localWorkspace, final TaskListener listener, final MeasurementConfiguration configWithRealGitVersions, final EnvVars envVars) {
      this.workspace = workspace;
      this.localWorkspace = localWorkspace;
      this.listener = listener;
      this.configWithRealGitVersions = configWithRealGitVersions;
      this.envVars = envVars;
   }

   public boolean measure() throws IOException, InterruptedException {
      final RemoteMeasurer remotePerformer = new RemoteMeasurer(configWithRealGitVersions, listener, envVars);
      boolean worked = workspace.act(remotePerformer);
      listener.getLogger().println("First stage result: " + worked);
      return worked;
   }
   
   public void copyFromRemote() throws IOException, InterruptedException {
      String remotePeassPath = ContinuousFolderUtil.getLocalFolder(new File(workspace.getRemote())).getPath();
      listener.getLogger().println("Remote Peass path: " + remotePeassPath);
      FilePath remotePeassFolder = new FilePath(workspace.getChannel(), remotePeassPath);
      int count = remotePeassFolder.copyRecursiveTo(new FilePath(localWorkspace));
      listener.getLogger().println("Copied " + count + " files from " + remotePeassFolder + " to " + localWorkspace.getAbsolutePath());
   }
   
   public ProjectChanges visualizeMeasurementData(final Run<?, ?> run)
         throws JAXBException, IOException, JsonParseException, JsonMappingException, JsonGenerationException {
      File dataFolder = new File(localWorkspace, configWithRealGitVersions.getVersion() + "_" + configWithRealGitVersions.getVersionOld());
      final HistogramReader histogramReader = new HistogramReader(configWithRealGitVersions, dataFolder);
      final Map<String, HistogramValues> measurements = histogramReader.readMeasurements();

      final File changeFile = new File(localWorkspace, "changes.json");
      final ProjectChanges changes;
      if (changeFile.exists()) {
         changes = Constants.OBJECTMAPPER.readValue(changeFile, ProjectChanges.class);
      } else {
         changes = new ProjectChanges();
      }

      final File statisticsFile = new File(localWorkspace, "statistics.json");
      final ProjectStatistics statistics = readStatistics(statisticsFile);

      TrendFileUtil.persistTrend(run, localWorkspace, statistics);

      final MeasureVersionAction action = new MeasureVersionAction(configWithRealGitVersions, changes.getVersion(configWithRealGitVersions.getVersion()), statistics, measurements);
      run.addAction(action);

      return changes;
   }
   
   private ProjectStatistics readStatistics(final File statisticsFile) throws IOException, JsonParseException, JsonMappingException {
      ProjectStatistics statistics;
      if (statisticsFile.exists()) {
         statistics = Constants.OBJECTMAPPER.readValue(statisticsFile, ProjectStatistics.class);
      } else {
         statistics = new ProjectStatistics();
      }
      return statistics;
   }

   
   public void rca(final Run<?, ?> run, final ProjectChanges changes, final RCAStrategy rcaStrategy) throws IOException, InterruptedException, Exception {
      final CauseSearcherConfig causeSearcherConfig = new CauseSearcherConfig(null, true, true, 0.01, false, true, rcaStrategy);

      RemoteRCA remoteRCAExecutor = new RemoteRCA(configWithRealGitVersions, causeSearcherConfig, changes, listener, envVars);
      boolean rcaWorked = workspace.act(remoteRCAExecutor);
      if (!rcaWorked) {
         run.setResult(Result.FAILURE);
         return;
      }

      copyFromRemote();

      final RCAVisualizer rcaVisualizer = new RCAVisualizer(configWithRealGitVersions, localWorkspace, changes, run);
      rcaVisualizer.visualizeRCA();
   }

}
