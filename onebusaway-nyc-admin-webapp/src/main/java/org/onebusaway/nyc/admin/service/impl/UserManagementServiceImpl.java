package org.onebusaway.nyc.admin.service.impl;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.onebusaway.nyc.admin.model.ui.UserDetail;
import org.onebusaway.nyc.admin.service.UserManagementService;
import org.onebusaway.nyc.admin.util.UserRoles;
import org.onebusaway.users.impl.authentication.AdaptivePasswordEncoder;
import org.onebusaway.users.model.User;
import org.onebusaway.users.model.UserIndex;
import org.onebusaway.users.model.UserRole;
import org.onebusaway.users.services.StandardAuthoritiesService;
import org.onebusaway.users.services.UserDao;
import org.onebusaway.users.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.encoding.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link UserManagementService}
 * @author abelsare
 *
 */
@Component
public class UserManagementServiceImpl implements UserManagementService {

	private SessionFactory _sessionFactory;
	private StandardAuthoritiesService authoritiesService;
	private UserDao userDao;
	private UserService userService;
	private PasswordEncoder passwordEncoder;
	private boolean _hasCryptoEncoder = false;
	
	
	private static final Logger log = LoggerFactory.getLogger(UserManagementServiceImpl.class);

	@Override
	@Transactional(readOnly=true)
	public List<String> getUserNames(final String searchString) {
		String hql = "select ui.id.value from UserIndex ui where ui.id.value like :searchString and " +
				"ui.id.type = 'username'";
		Query query = getSession().createQuery(hql);
		query.setParameter("searchString", "%" +searchString + "%");
		log.debug("Returning user names matching with string : {}", searchString);
		return query.list();
	}
	
	@Override
	@Transactional(readOnly=true)
	public UserDetail getUserDetail(final String userName) {

		Criteria criteria = getSession().createCriteria(User.class)
							.createCriteria("userIndices")
							.add(Restrictions.like("id.value", userName));

		List<User> users = criteria.list();

		UserDetail userDetail = null;
		
		if(!users.isEmpty()) {
			userDetail = buildUserDetail(users.get(0));
		}
		
		log.debug("Returning user details for user : {}", userName);
		
		return userDetail;
		
	}

	private UserDetail buildUserDetail(User user) {
		UserDetail userDetail = new UserDetail();
		
		userDetail.setId(user.getId());
		
		for(UserIndex userIndex : user.getUserIndices()) {
			userDetail.setUserName(userIndex.getId().getValue());
		}
		
		for(UserRole role : user.getRoles()) {
			//There should be only one role
			userDetail.setRole(role.getName());
		}
		
		return userDetail;
	}

	@Override
	@Transactional
	public void disableOperatorRole(User user) {
		UserRole operatorRole = authoritiesService.getUserRoleForName(StandardAuthoritiesService.USER);
		
		Set<UserRole> roles = user.getRoles();
		
		if(roles.remove(operatorRole)) {
			userDao.saveOrUpdateUser(user);
		}
	}
	
	@Override
	@Transactional
	public boolean createUser(String userName, String password, boolean admin) {
		UserIndex userIndex = userService.getOrCreateUserForUsernameAndPassword(userName, password);

		if(userIndex == null)
			return false;

		if(admin) {
			User user = userIndex.getUser();
			//Enable admin role
			userService.enableAdminRoleForUser(user, false);
			//Disable operator role. User can either be admin or operator but not both
			disableOperatorRole(user);
		}

		log.info("User '{}' created successfully", userName);
		
		return true;
	}
	
	@Override
	@Transactional
	public boolean updateUser(UserDetail userDetail) {
		
		User user = userDao.getUserForId(userDetail.getId());
		
		if(user == null) {
			log.info("User '{}' does not exist in the system", userDetail.getUserName());
			return false;
		}

		//Update user password
		String salt = _hasCryptoEncoder ? null : userDetail.getUserName(); 
		if(StringUtils.isNotBlank(userDetail.getPassword())) {
			String credentials = passwordEncoder.encodePassword(userDetail.getPassword(), salt);
			for(UserIndex userIndex : user.getUserIndices()) {
				userIndex.setCredentials(credentials);
			}
		}
		
		//Update user role
		updateRole(userDetail.getRole(), user);
		
		userDao.saveOrUpdateUser(user);
		
		log.info("User '{}' updated successfully", userDetail.getUserName());
		
		return true;
	}
	
	@Override
	@Transactional
	public boolean deactivateUser(UserDetail userDetail) {
		User user = userDao.getUserForId(userDetail.getId());
		
		if(user == null) {
			log.info("User '{}' does not exist in the system", userDetail.getUserName());
			return false;
		}
		
		//Delete user indices so that a user cannot authenticate even if 
		//user record itself is still present
		for(Iterator<UserIndex> it = user.getUserIndices().iterator(); it.hasNext();) {
			UserIndex userIndex = it.next();
			userDao.deleteUserIndex(userIndex);
			it.remove();
		}
		
		userDao.saveOrUpdateUser(user);
		
		log.info("User '{}' deactivated successfully", userDetail.getUserName());
		
		return true;
	}

	private void updateRole(String role, User user) {
		boolean updateRole = false;
		UserRole currentRole = null;

		Set<UserRole> userRoles = user.getRoles();
		
		for(UserRole userRole : userRoles) {
			//There should be only one role
			if(!(userRole.getName().equals(role))) {
				updateRole = true;
				currentRole = userRole;
			}
		}

		if(updateRole) {
			//Remove current role and add the new role
			userRoles.remove(currentRole);
			
			UserRoles newRole = UserRoles.valueOf(role);
			
			switch(newRole) {
				case ROLE_ANONYMOUS :
					userRoles.add(authoritiesService.getAnonymousRole());
					break;
				
				case ROLE_USER :
					userRoles.add(authoritiesService.getUserRole());
					break;
				
				case ROLE_ADMINISTRATOR :
					userRoles.add(authoritiesService.getAdministratorRole());
					break;	
			}
		}
	}

	@Autowired
	public void setSesssionFactory(SessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	private Session getSession(){
		return _sessionFactory.getCurrentSession();
	}

	/**
	 * @param authoritiesService the authoritiesService to set
	 */
	@Autowired
	public void setAuthoritiesService(StandardAuthoritiesService authoritiesService) {
		this.authoritiesService = authoritiesService;
	}

	/**
	 * @param userDao the userDao to set
	 */
	@Autowired
	public void setUserDao(UserDao userDao) {
		this.userDao = userDao;
	}
	

	/**
	 * Injects {@link UserService}
	 * @param userService the userService to set
	 */
	@Autowired
	public void setUserService(UserService userService) {
		this.userService = userService;
	}

	/**
	 * @param passwordEncoder the passwordEncoder to set
	 */
	@Autowired
	public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}
	
	public void setPasswordEncoder(Object passwordEncoder) {
	      Assert.notNull(passwordEncoder, "passwordEncoder cannot be null");
	      
	      if (passwordEncoder instanceof org.springframework.security.crypto.password.PasswordEncoder) {
	    	  _hasCryptoEncoder = true;
	      }
	      
	      AdaptivePasswordEncoder adpativePasswordEncoder = new AdaptivePasswordEncoder();
	      setPasswordEncoder(adpativePasswordEncoder.getPasswordEncoder(passwordEncoder));
	  }

}
