package org.mifosplatform.billing.billingorder.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.mifosplatform.billing.billingorder.commands.BillingOrderCommand;
import org.mifosplatform.billing.billingorder.data.BillingOrderData;
import org.mifosplatform.billing.billingorder.data.GenerateInvoiceData;
import org.mifosplatform.billing.billingorder.data.ProcessDate;
import org.mifosplatform.billing.billingorder.domain.Invoice;
import org.mifosplatform.billing.billingorder.exceptions.BillingOrderNoRecordsFoundException;
import org.mifosplatform.billing.billingorder.serialization.BillingOrderCommandFromApiJsonDeserializer;
import org.mifosplatform.billing.clientbalance.data.ClientBalanceData;
import org.mifosplatform.billing.clientbalance.service.ClientBalanceReadPlatformService;
import org.mifosplatform.billing.transactionhistory.service.TransactionHistoryWritePlatformService;
import org.mifosplatform.infrastructure.core.api.JsonCommand;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResult;
import org.mifosplatform.infrastructure.core.data.CommandProcessingResultBuilder;
import org.mifosplatform.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceClient {

	private BillingOrderReadPlatformService billingOrderReadPlatformService;
	private GenerateBillingOrderService generateBillingOrderService;
	private BillingOrderWritePlatformService billingOrderWritePlatformService;
	private ClientBalanceReadPlatformService clientBalanceReadPlatformService;
	private PlatformSecurityContext context;
	private BillingOrderCommandFromApiJsonDeserializer apiJsonDeserializer;
	private TransactionHistoryWritePlatformService transactionHistoryWritePlatformService; 
	
	
	
	
	@Autowired
	InvoiceClient(BillingOrderReadPlatformService billingOrderReadPlatformService,GenerateBillingOrderService generateBillingOrderService,
			BillingOrderWritePlatformService billingOrderWritePlatformService,ClientBalanceReadPlatformService clientBalanceReadPlatformService,
			final PlatformSecurityContext context,BillingOrderCommandFromApiJsonDeserializer apiJsonDeserializer,TransactionHistoryWritePlatformService transactionHistoryWritePlatformService) {
		this.billingOrderReadPlatformService = billingOrderReadPlatformService;
		this.generateBillingOrderService = generateBillingOrderService;
		this.billingOrderWritePlatformService = billingOrderWritePlatformService;
		this.context = context;
		this.clientBalanceReadPlatformService = clientBalanceReadPlatformService;
		this.apiJsonDeserializer=apiJsonDeserializer;
		this.transactionHistoryWritePlatformService=transactionHistoryWritePlatformService;
		//this.invoiceClient = invoiceClient;
	}
	public BigDecimal invoicingSingleClient(Long clientId, LocalDate processDate) {

		     // Get list of qualified orders
    		List<BillingOrderData> billingOrderDatas= billingOrderReadPlatformService.retrieveOrderIds(clientId, processDate);
		         if (billingOrderDatas.size() == 0) {
			          throw new BillingOrderNoRecordsFoundException();
		          }
		             BigDecimal invoiceAmount=BigDecimal.ZERO;
		             Date nextBillableDate=null;
		               for (BillingOrderData billingOrderData : billingOrderDatas) {
		            	   
                                  nextBillableDate=billingOrderData.getNextBillableDate();		            	
		            	   while(processDate.toDate().after(nextBillableDate) || processDate.toDate().compareTo(nextBillableDate) == 0){
		            	  
          	                   GenerateInvoiceData invoice=invoiceServices(billingOrderData,clientId,processDate);
                 	             
          	                   if(invoice!=null){
          	            	
	                               invoiceAmount=invoiceAmount.add(invoice.getInvoiceAmount());
	                               nextBillableDate=invoice.getNextBillableDay();
          	            }
		}
		               }
		return invoiceAmount;
	}
	
	public GenerateInvoiceData invoiceServices(BillingOrderData billingOrderData,Long clientId,LocalDate processDate){
		
			
			// Charges
			List<BillingOrderData> products = this.billingOrderReadPlatformService.retrieveBillingOrderData(clientId, processDate,billingOrderData.getOrderId());
			List<BillingOrderCommand> billingOrderCommands = this.generateBillingOrderService.generatebillingOrder(products);
			//List<BillingOrder> listOfBillingOrders = billingOrderWritePlatformService.createBillingProduct(billingOrderCommands);
			// Invoice
			Invoice invoice = this.generateBillingOrderService.generateInvoice(billingOrderCommands);
			// Client Balance
			// List<ClientBalanceData> clientBalancesDatas = adjustmentReadPlatformService.retrieveAllAdjustments(clientId);
			List<ClientBalanceData> clientBalancesDatas = clientBalanceReadPlatformService.retrieveAllClientBalances(clientId);

			//Update Client Balance
			this.billingOrderWritePlatformService.updateClientBalance(invoice,clientBalancesDatas);
		//	Invoice invoice = billingOrderWritePlatformService.createInvoice(invoiceCommand, clientBalancesDatas);
			// Update invoice-tax
			//billingOrderWritePlatformService.updateInvoiceTax(invoice, billingOrderCommands, listOfBillingOrders);
			// Update charge
			// billingOrderWritePlatformService.updateInvoiceCharge(invoice,listOfBillingOrders);
			 // Update order-price
			billingOrderWritePlatformService.updateBillingOrder(billingOrderCommands);
			 billingOrderWritePlatformService.updateOrderPrice(billingOrderCommands);
			 System.out.println("---------------------"+billingOrderCommands.get(0).getNextBillableDate());

			 transactionHistoryWritePlatformService.saveTransactionHistory(clientId,"Invoice", new Date(),"Amount:"
	    				+invoice.getInvoiceAmount(),"Charge Startdate:"+billingOrderCommands.get(0).getBillStartDate(),
	    				"Charge Enddate:"+billingOrderCommands.get(0).getEndDate());
			
			CommandProcessingResult entityIdentifier = billingOrderWritePlatformService.updateBillingOrder(billingOrderCommands);
               if(invoice.getInvoiceAmount() == null){
            	   return null;
               }
		return new GenerateInvoiceData(clientId,billingOrderCommands.get(0).getNextBillableDate(),invoice.getInvoiceAmount());
	}
	
	public Integer getQualifiedNumberOfTimes(LocalDate billStartDate , LocalDate processDate,String durationType ){
		int qualifiedNumberOfTimes = 0;
		int qualifiedBillingDays = Days.daysBetween(billStartDate, processDate).getDays()+1;
		
		if(durationType.equals("Week(s)")){
			if(qualifiedBillingDays<=7){
				qualifiedNumberOfTimes = 1;
			}else{
				qualifiedNumberOfTimes = (qualifiedBillingDays / 7)+1;
			}
			
		}else if(durationType.equals("Month(s)")){
			if(qualifiedBillingDays<=30){
				qualifiedNumberOfTimes = 1;
			}else{
				qualifiedNumberOfTimes = (qualifiedBillingDays / 30)+1;
			}
		}
		return qualifiedNumberOfTimes;
	}
	
	
	
	public CommandProcessingResult createInvoiceBill(JsonCommand command) {
		try {
			context.authenticatedUser();
			// validation not written
			 this.apiJsonDeserializer.validateForCreate(command.json());
			
			LocalDate processDate = ProcessDate.fromJson(command);
//	List<GenerateInvoiceData> invoiceDatas = this.billingOrderReadPlatformService.retrieveClientsWithOrders(processDate);
			/*if (invoiceDatas == null || invoiceDatas.size() == 0) {
				throw new BillingOrderNoRecordsFoundException();
			} else {
				for (GenerateInvoiceData invoiceData : invoiceDatas) {
					this.invoicingSingleClient(invoiceData.getClientId(), processDate);
				}

			}*/
			
				BigDecimal invoiceAmount=this.invoicingSingleClient(command.entityId(), processDate);
	                    invoiceAmount.doubleValue();
			
			return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withResourceIdAsString(invoiceAmount.toString()).build();

		} catch (DataIntegrityViolationException dve) {
			return CommandProcessingResult.empty();
		}

	}

}
