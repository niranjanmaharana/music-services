package com.niranzan.music.controller;

import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.niranzan.music.constant.AppConstants;
import com.niranzan.music.exceptions.DuplicateFieldException;
import com.niranzan.music.exceptions.InvalidFormatException;
import com.niranzan.music.exceptions.ResourceNotFoundException;
import com.niranzan.music.model.UserProfile;
import com.niranzan.music.model.UserSession;
import com.niranzan.music.security.jwt.JwtProvider;
import com.niranzan.music.service.UserService;
import com.niranzan.music.service.UserSessionService;
import com.niranzan.music.view.request.AuthRequest;
import com.niranzan.music.view.request.ResetPasswordRequestView;
import com.niranzan.music.view.request.UserRequestView;
import com.niranzan.music.view.response.JwtResponse;
import com.niranzan.music.view.response.SimpleResponseEntity;

import io.swagger.annotations.Api;

@Api(value = "Authentication", description = "Operations pertaining to login and logout.")
@RestController
@RequestMapping("/auth")
public class AuthController {
	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private UserService userService;
	@Autowired
	private JwtProvider jwtProvider;
	@Autowired
	private UserSessionService userSessionService;
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

	@PostMapping("/signin")
	public ResponseEntity<?> authenticateUser(@Valid @RequestBody AuthRequest loginRequest, @RequestHeader Map<String, String> headers, HttpServletRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		String jwt = jwtProvider.generateJwtToken(authentication);
		LOGGER.info("{} logged in.", authentication.getName());
		
		/*
		 * add an entry to the session table to keep track of user logins
		 * */
		Locale requestLocale = request.getLocale();		
		UserSession session = new UserSession();
		session.setUsername(authentication.getName());
		String country = requestLocale.getDisplayCountry();
		session.setCountry(StringUtils.isNotBlank(country) ? country : AppConstants.UNKNOWN_VAL);
		session.setUserAgent(headers.get("user-agent"));
		session.setOrigin(headers.get("origin"));
		session.setSuccess(true);
		userSessionService.saveSession(session);
		return ResponseEntity.ok(new JwtResponse(jwt));
	}
	
	@PostMapping("/signup")
	public ResponseEntity<SimpleResponseEntity> registerUser(@Valid @RequestBody UserRequestView request) {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		try {
			UserProfile user = userService.save(request);
			request.setId(user.getId());
			LOGGER.info(username + " registered user successfully.");
			return ResponseEntity.ok()
					.body(new SimpleResponseEntity(HttpStatus.OK.value(), AppConstants.SUCCESS_RESPONSE_MSG, request));
		} catch (DuplicateFieldException exception) {
			LOGGER.info("Exception occured while registering user: {}", exception.getMessage());
			return ResponseEntity.ok()
					.body(new SimpleResponseEntity(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), null));
		} catch (InvalidFormatException exception) {
			LOGGER.info("Exception occured while registering user: {}", exception.getMessage());
			return ResponseEntity.ok()
					.body(new SimpleResponseEntity(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), null));
		} catch(ResourceNotFoundException exception) {
			LOGGER.info("Exception occured while registering user: {}", exception.getMessage());
			return ResponseEntity.ok()
					.body(new SimpleResponseEntity(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), null));
		} catch (Exception exception) {
			LOGGER.info("Exception occured while registering user: {}", exception.getMessage());
			return ResponseEntity.ok().body(
					new SimpleResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error !", null));
		}
	}
	
	@PostMapping("/sendResetLink")
	public ResponseEntity<SimpleResponseEntity> sendResetLink(@RequestParam("email") String email, HttpServletRequest request, HttpServletResponse response) {
		return ResponseEntity.ok()
				.body(new SimpleResponseEntity(HttpStatus.OK.value(), AppConstants.SUCCESS_RESPONSE_MSG, this.userService.generateResetLink(email)));
	}
	
	@PostMapping("/validateResetLink")
	public ResponseEntity<SimpleResponseEntity> validateResetLink(@RequestParam("token") String token, HttpServletRequest request, HttpServletResponse response) {
		boolean isValid = this.userService.validateResetLink(token);
		SimpleResponseEntity responseEntity = new SimpleResponseEntity();
		responseEntity.setData(null);
		responseEntity.setStatusMessage(isValid ? AppConstants.FAILURE_RESPONSE_MSG : AppConstants.SUCCESS_RESPONSE_MSG);
		responseEntity.setStatusCode(isValid ? HttpStatus.NOT_FOUND.value() : HttpStatus.OK.value());
		return ResponseEntity.ok()
				.body(responseEntity);
	}
	
	@PostMapping("/resetPassword")
	public ResponseEntity<SimpleResponseEntity> resetPassword(@RequestBody ResetPasswordRequestView requestView, HttpServletRequest request, HttpServletResponse response) {
		boolean isReset = this.userService.resetPassword(requestView);
		SimpleResponseEntity responseEntity = new SimpleResponseEntity();
		responseEntity.setData(null);
		responseEntity.setStatusMessage(isReset ? AppConstants.FAILURE_RESPONSE_MSG : AppConstants.SUCCESS_RESPONSE_MSG);
		responseEntity.setStatusCode(isReset ? HttpStatus.BAD_REQUEST.value() : HttpStatus.OK.value());
		return ResponseEntity.ok()
				.body(responseEntity);
	}
}