/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.billing.activationprocess.service;
import org.mifosplatform.billing.inventory.service.InventoryItemDetailsWritePlatformService;
import org.mifosplatform.billing.onetimesale.service.OneTimeSaleWritePlatformService;
import org.mifosplatform.billing.order.service.OrderWritePlatformService;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;
import org.mifosplatform.infrastructure.core.exception.PlatformDataIntegrityException;
import org.mifosplatform.infrastructure.core.serialization.FromJsonHelper;
import org.mifosplatform.infrastructure.security.service.PlatformSecurityContext;
import org.mifosplatform.portfolio.client.domain.ClientRepositoryWrapper;
import org.mifosplatform.portfolio.client.service.ClientWritePlatformService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
@Service
public class ActivationProcessWritePlatformServiceJpaRepositoryImpl implements ActivationProcessWritePlatformService {

    private final static Logger logger = LoggerFactory.getLogger(ActivationProcessWritePlatformServiceJpaRepositoryImpl.class);

    private final PlatformSecurityContext context;
    private final ClientRepositoryWrapper clientRepository;
    private FromJsonHelper fromJsonHelper;
    private final ClientWritePlatformService clientWritePlatformService;
    private final OneTimeSaleWritePlatformService oneTimeSaleWritePlatformService;
    private final InventoryItemDetailsWritePlatformService inventoryItemDetailsWritePlatformService;
    private final OrderWritePlatformService orderWritePlatformService;
    @Autowired
    public ActivationProcessWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context,
            final ClientRepositoryWrapper clientRepository,
            final FromJsonHelper fromJsonHelper,final ClientWritePlatformService clientWritePlatformService,
            final OneTimeSaleWritePlatformService oneTimeSaleWritePlatformService,final InventoryItemDetailsWritePlatformService inventoryItemDetailsWritePlatformService,
            final OrderWritePlatformService orderWritePlatformService) {
        this.context = context;
        this.clientRepository = clientRepository;
        this.fromJsonHelper=fromJsonHelper;
        this.clientWritePlatformService=clientWritePlatformService;
        this.oneTimeSaleWritePlatformService=oneTimeSaleWritePlatformService;
        this.inventoryItemDetailsWritePlatformService=inventoryItemDetailsWritePlatformService;
        this.orderWritePlatformService=orderWritePlatformService;
    }

    private void handleDataIntegrityIssues(final JsonCommand command, final DataIntegrityViolationException dve) {

        Throwable realCause = dve.getMostSpecificCause();
        if (realCause.getMessage().contains("external_id")) {

            final String externalId = command.stringValueOfParameterNamed("externalId");
            throw new PlatformDataIntegrityException("error.msg.client.duplicate.externalId", "Client with externalId `" + externalId
                    + "` already exists", "externalId", externalId);
        } else if (realCause.getMessage().contains("account_no_UNIQUE")) {
            final String accountNo = command.stringValueOfParameterNamed("accountNo");
            throw new PlatformDataIntegrityException("error.msg.client.duplicate.accountNo", "Client with accountNo `" + accountNo
                    + "` already exists", "accountNo", accountNo);
        }else if (realCause.getMessage().contains("email_key")) {
            final String email = command.stringValueOfParameterNamed("email");
            throw new PlatformDataIntegrityException("error.msg.client.duplicate.email", "Client with email `" + email
                    + "` already exists", "email", email);
        }

        logAsErrorUnexpectedDataIntegrityException(dve);
        throw new PlatformDataIntegrityException("error.msg.client.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource.");
    }

    @Transactional
    @Override
    public CommandProcessingResult activationProcess(final JsonCommand command) {

        try {
            context.authenticatedUser();
            CommandProcessingResult resultClient=null;
            CommandProcessingResult resultSale=null;
            CommandProcessingResult resultAllocate=null;
            CommandProcessingResult resultOrder=null;
            final JsonElement element = fromJsonHelper.parse(command.json());
	        JsonArray clientData = fromJsonHelper.extractJsonArrayNamed("client", element);
	        JsonArray saleData = fromJsonHelper.extractJsonArrayNamed("sale", element);
	        JsonArray allocateData = fromJsonHelper.extractJsonArrayNamed("allocate", element);
	        JsonArray bookOrder = fromJsonHelper.extractJsonArrayNamed("bookorder", element);
	        
	        
	       
	        for(JsonElement j:clientData){
           
	        	JsonCommand comm=new JsonCommand(null, j.toString(),j, fromJsonHelper, null, null, null, null, null, null, null, null, null, null, null);
	        	//final JsonElement element1 = fromJsonHelper.parse(comm.json());
	        
	        	resultClient=this.clientWritePlatformService.createClient(comm);
	        	
	        }
	        for(JsonElement sale:saleData){
	        	
	        	JsonCommand comm=new JsonCommand(null, sale.toString(),sale, fromJsonHelper, null, null, null, null, null, null, null, null, null, null, null);
	        	resultSale=this.oneTimeSaleWritePlatformService.createOneTimeSale(comm,resultClient.getClientId());
	        
	        }
	         for(JsonElement allocate:allocateData){
	        	  JsonObject jsonObject =new JsonObject();
	        	  Long itemMasterId=fromJsonHelper.extractLongNamed("itemMasterId",allocate);
	        	  Long quantity=fromJsonHelper.extractLongNamed("quantity",allocate);
	        	 JsonArray serialData = fromJsonHelper.extractJsonArrayNamed("serialNumber", allocate);
	        	 for(JsonElement je:serialData){
	        	/*	String serialNsumber1=fromJsonHelper.extractStringNamed("serialNumber", je);
	        		 String status=fromJsonHelper.extractStringNamed("status", je);
	        		 Long itemMasterId=fromJsonHelper.extractLongNamed("itemMasterId", je);
	        		 String isNewHw=fromJsonHelper.extractStringNamed("isNewHw", je);
	        		 
	        		 JsonObject serialNsumber=new JsonObject();
	        		 serialNsumber.addProperty("serialNumber", serialNsumber1);
	        		 serialNsumber.addProperty("status", status);
	        		 serialNsumber.addProperty("itemMasterId", itemMasterId);
	        		 serialNsumber.addProperty("isNewHw", isNewHw);*/
	        		 JsonObject serialNumber=je.getAsJsonObject();
	        		 serialNumber.addProperty("clientId", resultClient.getClientId());
	        		 serialNumber.addProperty("orderId", resultSale.resourceId());
	        		 //JsonElement serialNumber1 = fromJsonHelper.parse(serialNumber.toString());
	        	     
	        	 }
	        	 jsonObject.addProperty("itemMasterId", itemMasterId);
	        	 jsonObject.addProperty("quantity", quantity);
	        	 jsonObject.add("serialNumber", serialData);
	        	 
	        	 
	        	 
	        	 JsonCommand comm=new JsonCommand(null, jsonObject.toString(),allocate, fromJsonHelper, null, null, null, null, null, null, null, null, null, null, null);
	        	 
	        	 resultAllocate=this.inventoryItemDetailsWritePlatformService.allocateHardware(comm);
	         }
	         for(JsonElement order:bookOrder){
		        	
		        	JsonCommand comm=new JsonCommand(null, order.toString(),order, fromJsonHelper, null, null, null, null, null, null, null, null, null, null, null);
		        	resultOrder=this.orderWritePlatformService.createOrder(resultClient.getClientId(),comm);
		        
		        }
	        return resultClient;

           
        } catch (DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve);
            return new CommandProcessingResult(1l).empty();
        }
	
    }

    private void logAsErrorUnexpectedDataIntegrityException(final DataIntegrityViolationException dve) {
        logger.error(dve.getMessage(), dve);
    }
}
