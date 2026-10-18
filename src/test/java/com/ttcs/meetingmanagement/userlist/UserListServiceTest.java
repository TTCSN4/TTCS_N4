
package com.ttcs.meetingmanagement.userlist;

import com.ttcs.meetingmanagement.userlist.UserListResponse.UserSummary;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserListServiceTest {

    private final UserListRepository repository =
            mock(UserListRepository.class);

    private final UserListService service =
            new UserListService(repository);

    // Du lieu gia lap, khong ket noi MySQL
    private UserSummary sampleUser() {
        return new UserSummary(
                "U001",
                "Nguyen Van A",
                "a@example.com",
                "0123456789",
                "ACTIVE",
                "R_ADMIN",
                "Admin",
                "D001",
                LocalDateTime.of(2026, 10, 1, 8, 0)
        );
    }

    // TEST 1: Phan trang khi co 25 nguoi dung
    @Test
    void shouldPaginateUsers() {

        when(repository.countUsers(null, null, null))
                .thenReturn(25L);

        when(repository.findUsers(
                null, null, null, 10, 10L
        )).thenReturn(List.of(sampleUser()));

        UserListResponse result =
                service.getUsers(1, 10, null, null, null);

        assertEquals(1, result.page());
        assertEquals(10, result.size());
        assertEquals(25L, result.totalElements());
        assertEquals(3, result.totalPages());

        assertFalse(result.first());
        assertFalse(result.last());

        assertEquals(1, result.content().size());

        verify(repository).findUsers(
                null, null, null, 10, 10L
        );
    }

    // TEST 2: Tim kiem va loc ket hop
    @Test
    void shouldNormalizeSearchAndFilters() {

        when(repository.countUsers(
                "Nguyen", "ACTIVE", "R_ADMIN"
        )).thenReturn(1L);

        when(repository.findUsers(
                "Nguyen", "ACTIVE", "R_ADMIN",
                10, 0L
        )).thenReturn(List.of(sampleUser()));

        UserListResponse result = service.getUsers(
                0,
                10,
                "  Nguyen  ",
                " active ",
                " R_ADMIN "
        );

        assertEquals(1L, result.totalElements());
        assertEquals(1, result.content().size());

        verify(repository).countUsers(
                "Nguyen", "ACTIVE", "R_ADMIN"
        );

        verify(repository).findUsers(
                "Nguyen", "ACTIVE", "R_ADMIN",
                10, 0L
        );
    }

    // TEST 3: Khong co nguoi dung phu hop
    @Test
    void shouldReturnEmptyPage() {

        when(repository.countUsers(null, null, null))
                .thenReturn(0L);

        when(repository.findUsers(
                null, null, null, 10, 0L
        )).thenReturn(List.of());

        UserListResponse result =
                service.getUsers(0, 10, null, null, null);

        assertEquals(0L, result.totalElements());
        assertEquals(0, result.totalPages());

        assertTrue(result.content().isEmpty());
        assertTrue(result.first());
        assertTrue(result.last());
    }

    // TEST 4: Kiem tra trang cuoi
    @Test
    void shouldIdentifyLastPage() {

        when(repository.countUsers(null, null, null))
                .thenReturn(21L);

        when(repository.findUsers(
                null, null, null, 10, 20L
        )).thenReturn(List.of(sampleUser()));

        UserListResponse result =
                service.getUsers(2, 10, null, null, null);

        assertEquals(3, result.totalPages());
        assertEquals(2, result.page());

        assertFalse(result.first());
        assertTrue(result.last());

        assertEquals(1, result.content().size());
    }

    // TEST 5: Page khong duoc am
    @Test
    void shouldRejectNegativePage() {

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getUsers(
                                -1, 10, null, null, null
                        )
                );

        assertEquals(
                400,
                error.getStatusCode().value()
        );

        verifyNoInteractions(repository);
    }

    // TEST 6: Size phai nam trong khoang 1-100
    @Test
    void shouldRejectInvalidPageSize() {

        ResponseStatusException zeroSize =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getUsers(
                                0, 0, null, null, null
                        )
                );

        ResponseStatusException tooLarge =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getUsers(
                                0, 101, null, null, null
                        )
                );

        assertEquals(
                400,
                zeroSize.getStatusCode().value()
        );

        assertEquals(
                400,
                tooLarge.getStatusCode().value()
        );

        verifyNoInteractions(repository);
    }

    // TEST 7: Xem chi tiet nguoi dung theo ID
    @Test
    void shouldGetUserById() {

        when(repository.findById("U001"))
                .thenReturn(Optional.of(sampleUser()));

        UserSummary result =
                service.getUserById(" U001 ");

        assertEquals("U001", result.userId());
        assertEquals("Nguyen Van A", result.fullName());
        assertEquals("R_ADMIN", result.roleId());
        assertEquals("ACTIVE", result.status());

        verify(repository).findById("U001");
    }

    // TEST 8: Khong tim thay nguoi dung
    @Test
    void shouldReturn404WhenUserNotFound() {

        when(repository.findById("U999"))
                .thenReturn(Optional.empty());

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getUserById("U999")
                );

        assertEquals(
                404,
                error.getStatusCode().value()
        );
    }

    // TEST 9: Tu choi User ID rong
    @Test
    void shouldRejectBlankUserId() {

        ResponseStatusException error =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getUserById("   ")
                );

        assertEquals(
                400,
                error.getStatusCode().value()
        );

        verifyNoInteractions(repository);
    }
}
