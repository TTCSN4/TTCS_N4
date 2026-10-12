
package com.ttcs.meetingmanagement.rbac;

/**
 * Contract supplied by the authentication module.
 *
 * userId must be a verified USER.user_id from
 * the official TTCS_N4 ERD.
 */
public interface AuthenticatedUserIdentity {

    String getUserId();
}
