package org.mifosplatform.billing.clientbalance.service;

import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;

public interface ClientBalanceWritePlatformService {

	CommandProcessingResult addClientBalance(JsonCommand command);

}
