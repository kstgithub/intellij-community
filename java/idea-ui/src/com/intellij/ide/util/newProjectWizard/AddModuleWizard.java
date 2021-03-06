// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.newProjectWizard.modes.ImportMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.Step;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.projectImport.ProjectImportBuilder;
import com.intellij.projectImport.ProjectImportProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class AddModuleWizard extends AbstractProjectWizard {
  private ProjectImportProvider[] myImportProviders;
  private final ModulesProvider myModulesProvider;
  private WizardMode myWizardMode;

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(@Nullable Project project, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), project, filePath);
    myImportProviders = importProviders;
    myModulesProvider = DefaultModulesProvider.createForProject(project);
    initModuleWizard(project, filePath);
  }

  /**
   * @param project if null, the wizard will start creating new project, otherwise will add a new module to the existing project.
   */
  public AddModuleWizard(Project project, Component dialogParent, String filePath, ProjectImportProvider... importProviders) {
    super(getImportWizardTitle(project, importProviders), project, dialogParent);
    myImportProviders = importProviders;
    myModulesProvider = DefaultModulesProvider.createForProject(project);
    initModuleWizard(project, filePath);
  }

  private static String getImportWizardTitle(Project project, ProjectImportProvider... providers) {
    StringBuilder builder = new StringBuilder("Import ");
    builder.append(project == null ? "Project" : "Module");
    if (providers.length == 1) {
      builder.append(" from ").append(providers[0].getName());
    }
    return builder.toString();
  }

  private void initModuleWizard(@Nullable final Project project, @Nullable final String defaultPath) {
    myWizardContext.addContextListener(new WizardContext.Listener() {
      public void buttonsUpdateRequested() {
        updateButtons();
      }

      public void nextStepRequested() {
        doNextAction();
      }
    });

    myWizardMode = new ImportMode(myImportProviders);
    StepSequence sequence = myWizardMode.getSteps(myWizardContext, DefaultModulesProvider.createForProject(project));
    appendSteps(sequence);
    for (ProjectImportProvider provider : myImportProviders) {
      provider.getBuilder().setFileToImport(defaultPath);
    }
    if (myImportProviders.length == 1) {
      final ProjectImportBuilder builder = myImportProviders[0].getBuilder();
      myWizardContext.setProjectBuilder(builder);
      builder.setUpdate(getWizardContext().getProject() != null);
    }
    init();
  }

  private void appendSteps(@Nullable final StepSequence sequence) {
    if (sequence != null) {
      for (ModuleWizardStep step : sequence.getAllSteps()) {
        addStep(step);
      }
    }
  }

  @Override
  public StepSequence getSequence() {
    return myWizardMode.getSteps(myWizardContext, myModulesProvider);
  }

  @Nullable
  public static Sdk getMostRecentSuitableSdk(final WizardContext context) {
    if (context.getProject() == null) {
      List<Sdk> sdks = Arrays.asList(ProjectJdkTable.getInstance().getAllJdks());

      ProjectBuilder builder = context.getProjectBuilder();
      if (builder != null) {
        sdks = ContainerUtil.filter(sdks, sdk -> builder.isSuitableSdkType(sdk.getSdkType()));
      }

      Map<SdkTypeId, List<Sdk>> sdksByType = sdks.stream().collect(groupingBy(Sdk::getSdkType, mapping(Function.identity(), toList())));
      Map.Entry<SdkTypeId, List<Sdk>> pair = ContainerUtil.getFirstItem(sdksByType.entrySet());
      if (pair != null) {
        return pair.getValue().stream().max(pair.getKey().versionComparator()).orElse(null);
      }
    }

    return null;
  }

  @NotNull
  public WizardContext getWizardContext() {
    return myWizardContext;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "NewModule_or_Project.wizard";
  }

  /**
   * Allows to ask current wizard to move to the desired step.
   *
   * @param filter  closure that allows to indicate target step - is called with each of registered steps and is expected
   *                to return {@code true} for the step to go to
   * @return        {@code true} if current wizard is navigated to the target step; {@code false} otherwise
   */
  public boolean navigateToStep(@NotNull com.intellij.util.Function<Step, Boolean> filter) {
    for (int i = 0, myStepsSize = mySteps.size(); i < myStepsSize; i++) {
      ModuleWizardStep step = mySteps.get(i);
      if (filter.fun(step) != Boolean.TRUE) {
        continue;
      }

      // Update current step.
      myCurrentStep = i;
      updateStep();
      return true;
    }
    return false;
  }

  @TestOnly
  public void commit() {
    commitStepData(getCurrentStepObject());
  }
}