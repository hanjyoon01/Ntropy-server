package com.ntropy.account.mapper;

import java.util.List;

import com.ntropy.account.domain.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    void upsert(Account account);

    /** {@link #upsert}의 다중 VALUES bulk 버전 (이슈 #233). 계좌 수와 무관하게 쿼리 1회로 저장한다. */
    void upsertAll(@Param("list") List<Account> accounts);

    void updateAccountDetails(Account account);

    Account findByConnectionIdAndAccountNoHash(@Param("codefConnectionId") Long codefConnectionId,
                                               @Param("accountNoHash") String accountNoHash);

    /** {@link #findByConnectionIdAndAccountNoHash}의 일괄 조회 버전 (이슈 #233). */
    List<Account> findByConnectionIdAndAccountNoHashes(@Param("codefConnectionId") Long codefConnectionId,
                                                        @Param("accountNoHashes") List<String> accountNoHashes);

    Account findByIdAndUserIdAndProvider(@Param("id") Long id,
                                         @Param("userId") Long userId,
                                         @Param("provider") String provider);

    List<Account> findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    boolean existsAnyByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    void deleteByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);
}
