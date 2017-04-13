/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.cas.support.pac4j.authentication.handler.support;

import io.cos.cas.adaptors.postgres.handlers.OpenScienceFrameworkInstitutionHandler;
import org.apache.commons.lang3.StringUtils;
import org.jasig.cas.authentication.BasicCredentialMetaData;
import org.jasig.cas.authentication.DefaultHandlerResult;
import org.jasig.cas.authentication.HandlerResult;
import org.jasig.cas.authentication.PreventedException;
import org.jasig.cas.support.pac4j.authentication.principal.ClientCredential;
import org.pac4j.core.client.Clients;
import org.pac4j.core.profile.UserProfile;

import javax.security.auth.login.FailedLoginException;
import javax.validation.constraints.NotNull;
import java.security.GeneralSecurityException;

/**
 * Specialized handler which builds the authenticated user directly from the retrieved user profile.
 *
 * @author Jerome Leleu
 * @author Longze Chen
 * @since 4.1.5
 */
public class ClientAuthenticationHandler extends AbstractClientAuthenticationHandler {

    /** Whether to use the typed identifier (by default) or just the identifier. */
    private boolean typedIdUsed = true;

    /** The handler for osf institution. */
    @NotNull
    private final OpenScienceFrameworkInstitutionHandler institutionHandler;

    /**
     * Define the clients.
     *
     * @param theInstitutionHandler The handler for osf institution
     * @param theClients The clients for authentication
     */
    public ClientAuthenticationHandler(
            final OpenScienceFrameworkInstitutionHandler theInstitutionHandler,
            final Clients theClients
    ) {
        super(theClients);
        this.institutionHandler = theInstitutionHandler;
    }

    /**
     * {@inheritDoc}
     *
     * Note on customization:
     *
     * 1.   The default behavior for building a principal by id is:
     *          id = isTypedIdUsed() ? profile.getTypedId() : profile.getId();
     *      When typedId (default) is used, the "class name" of the client profile is used.
     *
     *      For Example:
     *      An Orcid user's principal.getId() is "OrcidProfile#0000-1111-2222-3333".
     *      An OKState user's principal.getId() is "CasProfile#0000-1111-2222-3333".
     *
     *      This works for ORCiD, Facebook, Twitter, etc. because there is only one such client.
     *      However, it fails to work with CAS clients since CAS cannot identify IdP from "CasProfile".
     *
     * 3.   Workaround:
     *
     *      The issue is caused by profile.getTypedId() uses class names by default.
     *      "OrcidProfile" comes from OrcidProfile.class.getSimpleName(),
     *      "CasProfile" comes from CasProfile.class.getSimpleName().
     *
     *      The solution is to use "client name" instead of "class name" by explicitly setting the id.
     *          id = clientName
     *      Now, an OKState user's principal.getId() becomes "okstate" and we use the "mail" attribute
     *      for identity.
     *
     * 3.   For clients considered as "institution", their auth flow is intercepted and redirected to
     *      our institution login flow `remoteAuthenticate` after successful authentication.
     *
     *      The institutionId and the released delegation attributes are ready in the principal which
     *      will be used by `remoteAuthenticate`.
     */
    @Override
    protected HandlerResult createResult(final ClientCredential credentials, final UserProfile profile)
            throws GeneralSecurityException, PreventedException {

        final String clientName = credentials.getCredentials().getClientName();
        final String id;

        if (clientName != null) {
            if (this.institutionHandler.isDelegatedInstitutionLogin(clientName)) {
                // customized behavior
                // institution clients are independent of authentication delegation protocol
                // set principal id to the client name, which is identical to the institution id
                id = clientName;
            } else {
                // default behavior
                // respect Typed ID flag and use profile's class name
                id = isTypedIdUsed() ? profile.getTypedId() : profile.getId();
            }
            if (StringUtils.isNotBlank(id)) {
                credentials.setUserProfile(profile);
                credentials.setTypedIdUsed(typedIdUsed);
                return new DefaultHandlerResult(
                        this,
                        new BasicCredentialMetaData(credentials),
                        this.principalFactory.createPrincipal(id, profile.getAttributes()));
            }
            throw new FailedLoginException("No identifier found for this user profile: " + profile);
        }
        throw new FailedLoginException("No client name found for this user profile: " + profile);
    }

    public boolean isTypedIdUsed() {
        return typedIdUsed;
    }

    public void setTypedIdUsed(final boolean typedIdUsed) {
        this.typedIdUsed = typedIdUsed;
    }
}
