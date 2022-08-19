package edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.application.jobs;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.ConfidentialityVariableCharacterisation;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.dictionary.DictionaryPackage;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.dictionary.PCMDataDictionary;
import org.palladiosimulator.dataflow.confidentiality.pcm.model.confidentiality.expression.LhsEnumCharacteristicReference;
import org.palladiosimulator.dataflow.confidentiality.transformation.workflow.blackboards.KeyValueMDSDBlackboard;
import org.palladiosimulator.dataflow.dictionary.characterized.DataDictionaryCharacterized.EnumCharacteristicType;
import org.palladiosimulator.dataflow.dictionary.characterized.DataDictionaryCharacterized.Enumeration;
import org.palladiosimulator.dataflow.dictionary.characterized.DataDictionaryCharacterized.Literal;
import org.palladiosimulator.pcm.allocation.Allocation;
import org.palladiosimulator.pcm.seff.SetVariableAction;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;
import org.palladiosimulator.pcm.usagemodel.UsageModel;
import org.palladiosimulator.pcm.usagemodel.UsageScenario;

import de.uka.ipd.sdq.workflow.jobs.AbstractBlackboardInteractingJob;
import de.uka.ipd.sdq.workflow.jobs.CleanupFailedException;
import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.ModelLocation;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.ActionSequenceFinderImpl;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.CharacteristicsCalculator;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.CharacteristicsQueryEngine;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionBasedQueryImpl;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.ActionBasedQueryResult;
import edu.kit.kastel.dsis.fluidtrust.casestudy.pcs.analysis.dto.CharacteristicValue;

public class RunJavaBasedDestinationAnalysis extends AbstractBlackboardInteractingJob<KeyValueMDSDBlackboard> {

    private final ModelLocation usageModelLocation;
	private final ModelLocation allocationModelLocation;
	private final String allCharacteristicsResultKey;
	private final String violationResultKey;
	private final String scenario;
	private final Map<String, String> variables;

	public RunJavaBasedDestinationAnalysis(ModelLocation usageModelLocation, ModelLocation allocationModelLocation,
			String allCharacteristicsResultKey, String violationResultKey, String scenario,
			Map<String, String> variables) {
		this.usageModelLocation = usageModelLocation;
		this.allocationModelLocation = allocationModelLocation;
		this.allCharacteristicsResultKey = allCharacteristicsResultKey;
		this.violationResultKey = violationResultKey;
		this.scenario = scenario;
		this.variables = variables;
	}

	@Override
	public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
		var usageModel = (UsageModel) getBlackboard().getContents(usageModelLocation).get(0);
		var allocationModel = (Allocation) getBlackboard().getContents(allocationModelLocation).get(0);
		var modelPartition = getBlackboard().getPartition(usageModelLocation.getPartitionID());
		var dataDictionaries = modelPartition.getElement(DictionaryPackage.eINSTANCE.getPCMDataDictionary()).stream()
				.filter(PCMDataDictionary.class::isInstance).map(PCMDataDictionary.class::cast)
				.collect(Collectors.toList());

		monitor.beginTask("Execute queries", 3);

		var queryEngine = createQueryEngine(usageModel, allocationModel);
		monitor.worked(1);

		var allCharacteristics = findAllCharacteristics(queryEngine, dataDictionaries);
		getBlackboard().put(allCharacteristicsResultKey, allCharacteristics);
		monitor.worked(1);

		var detectedViolations = findViolations(dataDictionaries, allCharacteristics);
		getBlackboard().put(violationResultKey, detectedViolations);
		monitor.worked(1);
		monitor.done();
	}

	private ActionBasedQueryResult findViolations(List<PCMDataDictionary> dataDictionaries,
			ActionBasedQueryResult allCharacteristics) throws JobFailedException {
		var enumCharacteristicTypes = getAllEnumCharacteristicTypes(dataDictionaries);
		var ctACObject = findByName(enumCharacteristicTypes, "Destination");
		var objects2 = findByName(enumCharacteristicTypes, "IncidentRates");
		var ctAssignedRoles = findByName(enumCharacteristicTypes, "Userroles");
//        var convertedPolicy = AccessControlPolicy.POLICY.keySet()
//            .stream()
//            .collect(Collectors.toMap(role -> ctAssignedRoles.getType()
//                .getLiterals()
//                .stream()
//                .filter(l -> l.getName()
//                    .equals(role.getName()))
//                .findFirst().get(),
//                    role -> AccessControlPolicy.POLICY.get(role)
//                        .stream()
//                        .map(obj -> ctACObject.getType().getLiterals().stream().filter(l -> l.getName().equals(obj.getName())).findFirst().get())
//                        .collect(Collectors.toSet())));

		var violations = new ActionBasedQueryResult();

		for (var resultEntry : allCharacteristics.getResults().entrySet()) {
			for (var queryResult : resultEntry.getValue()) {
				if (!(queryResult.getElement().getElement() instanceof SetVariableAction)) {
					continue;
				}
				var assignedRoles = queryResult.getNodeCharacteristics().stream()
						.filter(cv -> cv.getCharacteristicType() == ctAssignedRoles)
						.map(CharacteristicValue::getCharacteristicLiteral).collect(Collectors.toList());
				var acObjects = queryResult.getDataCharacteristics().values().stream().flatMap(Collection::stream)
						.filter(cv -> cv.getCharacteristicType() == ctACObject || cv.getCharacteristicType() == objects2)
						.map(CharacteristicValue::getCharacteristicLiteral).collect(Collectors.toList());
				var action = (SetVariableAction) queryResult.getElement().getElement();

				if (action.getEntityName().equals("virtualInspection")
						&& acObjects.stream().anyMatch(e -> e.getName().equals("Columbia")) &&
								acObjects.stream().anyMatch(e-> e.getName().equals("high"))) {
					violations.addResult(resultEntry.getKey(), queryResult);
				}

//                var accessibleAcObjects = assignedRoles.stream()
//                    .map(convertedPolicy::get)
//                    .flatMap(Collection::stream)
//                    .collect(Collectors.toSet());
//                if (!accessibleAcObjects.containsAll(acObjects)) {
//                    violations.addResult(resultEntry.getKey(), queryResult);
//                }

			}
		}

		return violations;
	}

	private EnumCharacteristicType findByName(Collection<EnumCharacteristicType> enumCharacteristicTypes, String name)
			throws JobFailedException {
		return enumCharacteristicTypes.stream().filter(ct -> ct.getName().equals(name)).findFirst()
				.orElseThrow(() -> new JobFailedException("Could not find " + name + " characteristic type."));
	}

	private ActionBasedQueryResult findAllCharacteristics(CharacteristicsQueryEngine queryEngine,
			Collection<PCMDataDictionary> dataDictionaries) {
		var allCharacteristicValues = getAllEnumCharacteristicTypes(dataDictionaries).stream()
				.flatMap(c -> c.getType().getLiterals().stream().map(l -> new CharacteristicValue(c, l)))
				.collect(Collectors.toList());

		var query = new ActionBasedQueryImpl(Objects::nonNull, allCharacteristicValues, allCharacteristicValues);
		return queryEngine.query(query);
	}

	private Collection<EnumCharacteristicType> getAllEnumCharacteristicTypes(
			Collection<PCMDataDictionary> dataDictionaries) {
		return dataDictionaries.stream().map(PCMDataDictionary::getCharacteristicTypes).flatMap(Collection::stream)
				.filter(EnumCharacteristicType.class::isInstance).map(EnumCharacteristicType.class::cast)
				.collect(Collectors.toList());
	}

	private CharacteristicsQueryEngine createQueryEngine(UsageModel usageModel, Allocation allocationModel) {
		var actionSequenceFinder = new ActionSequenceFinderImpl();

		Optional<UsageScenario> scenario = Optional.empty();
		if (this.scenario != null) {
			scenario = usageModel.getUsageScenario_UsageModel().stream()
					.filter(e -> e.getEntityName().equals(this.scenario)).findAny();
		}

		for (var test : scenario.get().getScenarioBehaviour_UsageScenario().getActions_ScenarioBehaviour()) {
			test.getEntityName();
			if (test instanceof EntryLevelSystemCall) {
				var call = (EntryLevelSystemCall) test;

				for (var t : call.getInputParameterUsages_EntryLevelSystemCall()) {

					var variableName = t.getNamedReference__VariableUsage().getReferenceName();

					if (variables.containsKey(variableName)) {
						var character = t.getVariableCharacterisation_VariableUsage();
						for (var c : character) {
							if (c instanceof ConfidentialityVariableCharacterisation) {
								var con = (ConfidentialityVariableCharacterisation) c;

								var lhs = (LhsEnumCharacteristicReference) con.getLhs();
								var enumType = (EnumCharacteristicType) lhs.getCharacteristicType();

								var literal = findValue(enumType, variables.get(variableName));
								lhs.setLiteral(literal);

							}
						}

					}
				}
			}
		}

		var actionSequences = scenario.isEmpty() ? actionSequenceFinder.findActionSequencesForUsageModel(usageModel)
				: actionSequenceFinder.findActionSequencesForUsageModel(scenario.get());
		var characteristicsCalculator = new CharacteristicsCalculator(allocationModel);
		var queryEngine = new CharacteristicsQueryEngine(characteristicsCalculator, actionSequences);
		return queryEngine;
	}

	private Literal findValue(EnumCharacteristicType enumeration, String value) {
		switch (enumeration.getName()) {
		case "IncidentRates":
			var rate = Integer.parseInt(value);
			if(rate < 10)
				value ="low";
			else if(rate < 100)
				value = "medium";
			else
				value = "high";
			break;

		default:
			break;
		}
		var literalValue = value;
		return enumeration.getType().	
				getLiterals().stream().filter(e -> e.getName().equals(literalValue)).findAny().get();
	}

	@Override
	public void cleanup(IProgressMonitor monitor) throws CleanupFailedException {
		// nothing to do here
	}

	@Override
	public String getName() {
		return "Analysis Run";
	}

}
