package net.chrisrichardson.eventstore.javaexamples.banking.backend.queryside.accounts;

import io.eventuate.DispatchedEvent;
import io.eventuate.EventHandlerMethod;
import io.eventuate.EventSubscriber;
import net.chrisrichardson.eventstore.javaexamples.banking.backend.common.accounts.AccountChangedEvent;
import net.chrisrichardson.eventstore.javaexamples.banking.backend.common.accounts.AccountCreditedEvent;
import net.chrisrichardson.eventstore.javaexamples.banking.backend.common.accounts.AccountDebitedEvent;
import net.chrisrichardson.eventstore.javaexamples.banking.backend.common.accounts.AccountOpenedEvent;
import net.chrisrichardson.eventstore.javaexamples.banking.backend.common.transactions.MoneyTransferCreatedEvent;
import net.chrisrichardson.eventstore.javaexamples.banking.common.accounts.AccountTransactionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static net.chrisrichardson.eventstore.javaexamples.banking.backend.queryside.accounts.MoneyUtil.toIntegerRepr;


@EventSubscriber(id="querySideEventHandlers")
public class AccountQueryWorkflow {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private AccountInfoUpdateService accountInfoUpdateService;

  public AccountQueryWorkflow(AccountInfoUpdateService accountInfoUpdateService) {
    this.accountInfoUpdateService = accountInfoUpdateService;
  }

  @EventHandlerMethod
  public void create(DispatchedEvent<AccountOpenedEvent> de) {
    AccountOpenedEvent event = de.getEvent();
    String id = de.getEntityId();
    String eventId = de.getEventId().asString();
    logger.info("**************** account version=" + id + ", " + eventId);
    BigDecimal initialBalance = event.getInitialBalance();

    String customerId =  event.getCustomerId();
    String title =  event.getTitle();
    String description =  event.getDescription();
    accountInfoUpdateService.create(id, customerId, title, initialBalance, description, eventId);
  }

  @EventHandlerMethod
  public void recordTransfer(DispatchedEvent<MoneyTransferCreatedEvent> de) {
    String eventId = de.getEventId().asString();
    String moneyTransferId = de.getEntityId();
    String fromAccountId = de.getEvent().getDetails().getFromAccountId();
    String toAccountId = de.getEvent().getDetails().getToAccountId();
    logger.info("**************** account version=" + fromAccountId + ", " + de.getEventId().asString());
    logger.info("**************** account version=" + toAccountId + ", " + de.getEventId().asString());

    AccountTransactionInfo ti = new AccountTransactionInfo(moneyTransferId,
            fromAccountId,
            toAccountId,
            toIntegerRepr(de.getEvent().getDetails().getAmount()),
            de.getEvent().getDetails().getDate(),
            de.getEvent().getDetails().getDescription());

    accountInfoUpdateService.addTransaction(eventId, fromAccountId, ti);
    accountInfoUpdateService.addTransaction(eventId, toAccountId, ti);
  }

  @EventHandlerMethod
  public void recordDebit(DispatchedEvent<AccountDebitedEvent> de) {
    saveChange(de, -1);
  }

  @EventHandlerMethod
  public void recordCredit(DispatchedEvent<AccountCreditedEvent> de) {
    saveChange(de, +1);
  }

  public <T extends AccountChangedEvent> void saveChange(DispatchedEvent<T> de, int delta) {
    String changeId = de.getEventId().asString();
    String transactionId = de.getEvent().getTransactionId();
    long amount = toIntegerRepr(de.getEvent().getAmount());

    long balanceDelta = amount * delta;
    AccountChangeInfo ci = new AccountChangeInfo(changeId, transactionId, de.getEvent().getClass().getSimpleName(), amount, balanceDelta);
    String accountId = de.getEntityId();
    logger.info("**************** account version=" + accountId + ", " + de.getEventId().asString());

    accountInfoUpdateService.updateBalance(accountId, changeId, balanceDelta, ci);
  }

}
