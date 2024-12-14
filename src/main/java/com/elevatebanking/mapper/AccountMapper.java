package com.elevatebanking.mapper;

import com.elevatebanking.dto.accounts.AccountDTOs;
import com.elevatebanking.entity.account.Account;
import com.elevatebanking.entity.base.interfaces.Status;
import com.elevatebanking.entity.enums.AccountStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    @Mapping(target = "userId", source = "user.id")
    AccountDTOs.AccountResponse accountToAccountResponse(Account account);

    AccountDTOs.AccountBalanceResponse accountToBalanceResponse(Account account);

    AccountDTOs.AccountSummaryResponse accountToSummaryResponse(Account account);

    List<AccountDTOs.AccountResponse> accountsToAccountResponses(List<Account> accounts);

    List<AccountDTOs.AccountSummaryResponse> accountsToSummaryResponses(List<Account> accounts);

    default AccountStatus mapStatus(Status status) {
        if (status == null) {
            return null;
        }
        return AccountStatus.valueOf(status.name());
    }
}
