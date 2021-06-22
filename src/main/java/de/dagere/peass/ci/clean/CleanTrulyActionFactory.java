package de.dagere.peass.ci.clean;

import java.util.Collection;
import java.util.Collections;

import de.dagere.peass.ci.clean.CleanTrulyAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import jenkins.model.TransientActionFactory;

@Extension
public class CleanTrulyActionFactory extends TransientActionFactory<Job> {

   @Override
   public Class<Job> type() {
      return Job.class;
   }

   @NonNull
   @Override
   public Collection<? extends Action> createFor(@NonNull final Job project) {
      return Collections.singleton(new CleanTrulyAction(project));
   }
}
