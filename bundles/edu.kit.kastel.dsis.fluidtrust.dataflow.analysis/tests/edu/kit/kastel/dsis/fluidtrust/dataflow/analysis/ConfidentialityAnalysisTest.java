package edu.kit.kastel.dsis.fluidtrust.dataflow.analysis;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;

import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;

public class ConfidentialityAnalysisTest extends TestBase {

	@Override
	protected List<String> getModelsPath() {
		// TODO Auto-generated method stub
		return new ArrayList<>();
	}

	@Override
	protected void assignValues(List<Resource> list) {
		// TODO Auto-generated method stub
	}
	
	@Test
	public void testOnlineShop() throws JobFailedException, UserCanceledException {
		System.out.println("\n------------------------------------------------------------------------------------");
		System.out.println("Running Online Shop Analysis:\n");
		
		final var allocationURI = TestInitializer.getModelURI("models/InternationalOnlineShop/default.allocation");
		final var usageURI = TestInitializer.getModelURI("models/InternationalOnlineShop/default.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI, new RunOnlineShopAnalysisJob());
		workflow.execute(new NullProgressMonitor());
	}
	
	@Test
	public void testBranchingOnlineShop() throws JobFailedException, UserCanceledException {
		System.out.println("\n------------------------------------------------------------------------------------");
		System.out.println("Running Branching Online Shop Analysis:\n");
		
		final var allocationURI = TestInitializer.getModelURI("models/BranchingOnlineShop/default.allocation");
		final var usageURI = TestInitializer.getModelURI("models/BranchingOnlineShop/default.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI, new RunOnlineShopAnalysisJob());
		workflow.execute(new NullProgressMonitor());
	}
	
	@Test
	public void testTravelPlanner() throws JobFailedException, UserCanceledException {
		System.out.println("\n------------------------------------------------------------------------------------");
		System.out.println("Running Travel Planner Analysis:\n");
		
		final var allocationURI = TestInitializer.getModelURI("models/TravelPlanner/travelPlanner.allocation");
		final var usageURI = TestInitializer.getModelURI("models/TravelPlanner/travelPlanner.usagemodel");
		final var workflow = new DataflowAnalysisWorkflow(allocationURI, usageURI, new RunTravelPlannerAnalysisJob());
		workflow.execute(new NullProgressMonitor());
	}

}
