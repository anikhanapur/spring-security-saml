/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.springframework.security.saml.spi;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml.SamlMessageProcessor;
import org.springframework.security.saml.SamlValidator;
import org.springframework.security.saml.config.LocalIdentityProviderConfiguration;
import org.springframework.security.saml.saml2.authentication.Assertion;
import org.springframework.security.saml.saml2.authentication.Response;
import org.springframework.security.saml.saml2.metadata.Endpoint;
import org.springframework.security.saml.saml2.metadata.IdentityProviderMetadata;
import org.springframework.security.saml.saml2.metadata.NameId;
import org.springframework.security.saml.saml2.metadata.ServiceProviderMetadata;

public class DefaultIdpInitiationProcessor extends SamlMessageProcessor<DefaultIdpInitiationProcessor> {

	private SamlValidator validator;
	private String postBindingTemplate;

	public SamlValidator getValidator() {
		return validator;
	}

	public DefaultIdpInitiationProcessor setValidator(SamlValidator validator) {
		this.validator = validator;
		return this;
	}

	public String getPostBindingTemplate() {
		return postBindingTemplate;
	}

	public DefaultIdpInitiationProcessor setPostBindingTemplate(String postBindingTemplate) {
		this.postBindingTemplate = postBindingTemplate;
		return this;
	}

	@Override
	protected ProcessingStatus process(HttpServletRequest request,
									   HttpServletResponse response) throws IOException {

		String entityId = request.getParameter("sp");
		//no authnrequest provided
		ServiceProviderMetadata metadata = getResolver().resolveServiceProvider(entityId);
		IdentityProviderMetadata local = getResolver().getLocalIdentityProvider(getNetwork().getBasePath
			(request));
		Assertion assertion = getAssertion(metadata, local, SecurityContextHolder.getContext()
			.getAuthentication());
		Response result = getResponse(metadata, local, assertion);
		String encoded = getTransformer().samlEncode(getTransformer().toXml(result), false);
		Map<String, String> model = new HashMap<>();
		model.put("action", getAcs(metadata));
		model.put("SAMLResponse", encoded);
		processHtml(request, response, getPostBindingTemplate(), model);
		return ProcessingStatus.STOP;
	}

	protected Assertion getAssertion(ServiceProviderMetadata metadata,
									 IdentityProviderMetadata local,
									 Authentication authentication) {
		String principal = authentication.getName();
		return getDefaults().assertion(metadata, local, null, principal, NameId.PERSISTENT);
	}

	protected Response getResponse(
		ServiceProviderMetadata metadata, IdentityProviderMetadata local,
		Assertion assertion
	) {
		return getDefaults().response(
			null,
			assertion,
			metadata,
			local
		);
	}

	protected String getAcs(ServiceProviderMetadata metadata) {
		List<Endpoint> acs = metadata.getServiceProvider().getAssertionConsumerService();
		Endpoint result = acs.stream().filter(e -> e.isDefault()).findFirst().orElse(null);
		if (result == null) {
			result = acs.get(0);
		}
		return result.getLocation();
	}

	@Override
	public boolean supports(HttpServletRequest request) {
		LocalIdentityProviderConfiguration idp = getConfiguration().getIdentityProvider();
		String prefix = idp.getPrefix();
		String path = prefix + "/init";
		return isUrlMatch(request, path) && request.getParameter("sp") != null;
	}
}
