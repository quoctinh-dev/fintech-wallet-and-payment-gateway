package com.fintech.wallet.modules.account.mapper;

import com.fintech.wallet.modules.account.dto.response.AccountResponse;
import com.fintech.wallet.modules.account.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "balance", source = "balance")
    AccountResponse toResponse(Account account, BigDecimal balance);
}