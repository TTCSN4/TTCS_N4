
package com.ttcs.meetingmanagement.userlist;

import com.ttcs.meetingmanagement.userlist.UserListResponse.UserSummary;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("@rbacAccess.isAdmin(authentication)")
public class UserListController {

    private final UserListService userListService;

    public UserListController(UserListService userListService) {
        this.userListService = userListService;
    }

    // API 1: Lay danh sach nguoi dung
    // Ho tro phan trang, tim kiem va loc.
    @GetMapping
    public UserListResponse getUsers(

            @RequestParam(
                    name = "page",
                    defaultValue = "0"
            )
            int page,

            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            )
            int size,

            @RequestParam(
                    name = "search",
                    required = false
            )
            String search,

            @RequestParam(
                    name = "status",
                    required = false
            )
            String status,

            @RequestParam(
                    name = "roleId",
                    required = false
            )
            String roleId
    ) {

        return userListService.getUsers(
                page,
                size,
                search,
                status,
                roleId
        );
    }

    // API 2: Xem thong tin co ban cua nguoi dung
    @GetMapping("/{userId}")
    public UserSummary getUserById(
            @PathVariable("userId") String userId
    ) {

        return userListService.getUserById(userId);
    }
}
