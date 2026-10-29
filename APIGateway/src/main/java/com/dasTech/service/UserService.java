package com.dasTech.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.dasTech.entity.User;
import com.dasTech.repository.UserRepo;

@Service
public class UserService implements UserDetailsService {

	@Autowired
	private UserRepo userRepo;
	

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User dbUser = userRepo.findByEmail(username);

		if (dbUser == null) {
			System.out.println("User Exist");
			
		}

		System.out.println("Email found: " + dbUser.getEmail());

		    return org.springframework.security.core.userdetails.User
		            .withUsername(dbUser.getEmail())
		            .password(dbUser.getPassword())
		            .authorities(Collections.emptyList())
		            .build();
		}

}