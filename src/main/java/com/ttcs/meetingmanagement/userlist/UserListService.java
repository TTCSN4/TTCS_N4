
package com.ttcs.meetingmanagement.userlist;

import com.ttcs.meetingmanagement.userlist.UserListResponse.UserSummary;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class UserListService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserListRepository repository;

    public UserListService(UserListRepository repository) {
        this.repository = repository;
    }

    // 1. Lay danh sach nguoi dung co phan trang,
    // tim kiem va loc theo trang thai / vai tro.
    @Transactional(readOnly = true)
    public UserListResponse getUsers(
            int page,
            int size,
            String search,
            String status,
            String roleId
    ) {

        // Kiem tra so trang
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be greater than or equal to 0"
            );
        }

        // Gioi han so nguoi dung moi trang
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }

        // 2. Chuan hoa cac tham so loc
        String normalizedSearch = normalize(search);

        String normalizedStatus = normalize(status);

        String normalizedRoleId = normalize(roleId);

        if (normalizedStatus != null) {
            normalizedStatus =
                    normalizedStatus.toUpperCase(Locale.ROOT);
        }

        // 3. Dem tong so nguoi dung thoa man
        // cac dieu kien tim kiem va loc
        long totalElements = repository.countUsers(
                normalizedSearch,
                normalizedStatus,
                normalizedRoleId
        );

        // 4. Tinh vi tri bat dau cua trang
        // Su dung long de tranh tran so int
        long offset = (long) page * size;

        // 5. Lay danh sach nguoi dung
        List<UserSummary> content = repository.findUsers(
                normalizedSearch,
                normalizedStatus,
                normalizedRoleId,
                size,
                offset
        );

        // 6. Tinh tong so trang
        // Chia nguyen va cong them 1 khi con du
        long pageCount = totalElements / size;

        if (totalElements % size != 0) {
            pageCount++;
        }

        int totalPages;

        try {
            totalPages = Math.toIntExact(pageCount);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Total page count exceeds supported range"
            );
        }

        // 7. Xac dinh trang dau va trang cuoi
        boolean first = page == 0;

        boolean last = totalPages == 0
                || page >= totalPages - 1;

        // 8. Tra ve ket qua phan trang
        return new UserListResponse(
                content,
                page,
                size,
                totalElements,
                totalPages,
                first,
                last
        );
    }

    // 9. Lay thong tin co ban cua mot nguoi dung
    @Transactional(readOnly = true)
    public UserSummary getUserById(String userId) {

        String normalizedId = normalize(userId);

        if (normalizedId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID is required"
            );
        }

        return repository.findById(normalizedId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found"
                        )
                );
    }

    // 10. Loai bo khoang trang dau/cuoi
    // Chuoi rong duoc coi la khong loc
    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
