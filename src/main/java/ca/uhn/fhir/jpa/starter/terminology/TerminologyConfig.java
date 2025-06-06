package ca.uhn.fhir.jpa.starter.terminology;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import ca.uhn.fhir.jpa.starter.AppProperties;
import ca.uhn.fhir.jpa.starter.common.StarterJpaConfig;
import ca.uhn.fhir.jpa.starter.common.validation.OnRemoteTerminologyPresent;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import jakarta.annotation.Nonnull;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Conditional(OnRemoteTerminologyPresent.class)
@Import(StarterJpaConfig.class)
public class TerminologyConfig {

	@Bean(name = "myHybridRemoteValidationSupportChain")
	public IValidationSupport addRemoteValidation(
			ValidationSupportChain theValidationSupport, FhirContext theFhirContext, AppProperties theAppProperties) {
		var values = theAppProperties.getRemoteTerminologyServicesMap().values();

		// If the remote terminology service is "*" and is the only one then forward all requests to the remote
		// terminology service
		if (values.size() == 1 && "*".equalsIgnoreCase(values.iterator().next().getSystem())) {
			var remoteSystem = values.iterator().next();
			theValidationSupport.addValidationSupport(
					0, new RemoteTerminologyServiceValidationSupport(theFhirContext, remoteSystem.getUrl())
			{
				@Override
				public @Nullable ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext, @Nullable ValueSetExpansionOptions theExpansionOptions, @NotNull String theValueSetUrlToExpand) throws ResourceNotFoundException {
					return super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetUrlToExpand);
				}

				@Override
				public @Nullable ValueSetExpansionOutcome expandValueSet(ValidationSupportContext theValidationSupportContext, @Nullable ValueSetExpansionOptions theExpansionOptions, @NotNull IBaseResource theValueSetToExpand) {
					return super.expandValueSet(theValidationSupportContext, theExpansionOptions, theValueSetToExpand);
				}
			})
			;

			return theValidationSupport;

			// If there are multiple remote terminology services, then add each one to the validation chain
		} else {
			values.forEach((remoteSystem) -> theValidationSupport.addValidationSupport(
					0, new RemoteTerminologyServiceValidationSupport(theFhirContext, remoteSystem.getUrl()) {
						@Override
						public boolean isCodeSystemSupported(
								ValidationSupportContext theValidationSupportContext, String theSystem) {
							return remoteSystem.getSystem().equalsIgnoreCase(theSystem);
						}

						@Override
						public CodeValidationResult validateCode(
								ValidationSupportContext theValidationSupportContext,
								ConceptValidationOptions theOptions,
								String theCodeSystem,
								String theCode,
								String theDisplay,
								String theValueSetUrl) {
							if (remoteSystem.getSystem().equalsIgnoreCase(theCodeSystem)) {
								return super.validateCode(
										theValidationSupportContext,
										theOptions,
										theCodeSystem,
										theCode,
										theDisplay,
										theValueSetUrl);
							}
							return null;
						}
					}));
		}
		return theValidationSupport;
	}
}
