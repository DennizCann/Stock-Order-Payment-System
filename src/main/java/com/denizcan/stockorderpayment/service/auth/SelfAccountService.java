package com.denizcan.stockorderpayment.service.auth;

import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class SelfAccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public record Profile(String username,String fullName,String roleName,String branchName,String revision,boolean reloginRequired) {}
    @Transactional(readOnly=true) public Profile current(String username) {
        return profile(users.findByUsername(username).filter(User::isEnabled).orElseThrow(SelfAccountService::unavailable),false);
    }
    @Transactional public Profile update(String currentUsername,String revision,String username,String fullName,String currentPassword) {
        AccountService.validateUsername(username);AccountService.validateFullName(fullName);
        User user=users.findByUsernameForUpdate(currentUsername).filter(User::isEnabled).orElseThrow(SelfAccountService::unavailable);
        if(!AccountService.EmployeeAccount.from(user).revision().equals(revision)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Hesap bilgileri değişmiş. Formu tekrar aç.");
        boolean rename=!user.getUsername().equals(username);
        if(rename) verifyPassword(user,currentPassword);
        if(users.existsByUsernameAndIdNot(username,user.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bu kullanıcı adı zaten kullanımda.");
        user.reviseAccount(username,fullName,user.getRole(),user.getBranch(),user.getEmployeeRole(),user.isEnabled());
        users.flush();return profile(user,rename);
    }
    @Transactional public void changePassword(String username,String current,String replacement) {
        AccountService.validatePassword(replacement);
        User user=users.findByUsernameForUpdate(username).filter(User::isEnabled).orElseThrow(SelfAccountService::unavailable);
        verifyPassword(user,current);
        if(passwords.matches(replacement,user.getPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Yeni parola mevcut paroladan farklı olmalı.");
        user.changePassword(passwords.encode(replacement));
    }
    private void verifyPassword(User user,String current) {
        if(current==null||current.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72||!passwords.matches(current,user.getPassword()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Mevcut parola doğru değil.");
    }
    private static Profile profile(User user,boolean relogin) {
        String role=user.getRole()==Role.ADMIN?"Yönetici":user.getEmployeeRole()!=null?user.getEmployeeRole().getName():user.getRole()==Role.BRANCH_EMPLOYEE?"Şube çalışanı":"Merkez çalışanı";
        return new Profile(user.getUsername(),user.getFullName(),role,user.getBranch()==null?null:user.getBranch().getName(),AccountService.EmployeeAccount.from(user).revision(),relogin);
    }
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Hesap kullanılamıyor.");}
}
