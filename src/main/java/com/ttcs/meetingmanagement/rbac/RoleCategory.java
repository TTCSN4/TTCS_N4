package com.ttcs.meetingmanagement.rbac;

import com.ttcs.meetingmanagement.model.Role;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public enum RoleCategory {
    ADMIN,
    PM,
    MEMBER;

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public static RoleCategory categorize(Role role) {
        if (role == null) {
            return null;
        }
        return categorize(role.getRoleId(), role.getRoleName());
    }

    public static RoleCategory categorize(String roleId, String roleName) {
        String idNorm = normalize(roleId);
        String nameNorm = normalize(roleName);

        if (isAdminText(idNorm) || isAdminText(nameNorm)) {
            return ADMIN;
        }
        if (isPmText(idNorm) || isPmText(nameNorm)) {
            return PM;
        }
        if (isMemberText(idNorm) || isMemberText(nameNorm)) {
            return MEMBER;
        }
        return null;
    }

    private static boolean isAdminText(String text) {
        if (text == null || text.isBlank()) return false;
        return text.equals("admin")
                || text.equals("role_admin")
                || text.contains("quan tri")
                || text.contains("administrator");
    }

    private static boolean isPmText(String text) {
        if (text == null || text.isBlank()) return false;
        return text.equals("pm")
                || text.equals("role_pm")
                || text.equals("booker")
                || text.equals("role_booker")
                || text.contains("dat lich")
                || text.contains("scheduler")
                || text.contains("project manager");
    }

    private static boolean isMemberText(String text) {
        if (text == null || text.isBlank()) return false;
        return text.equals("member")
                || text.equals("role_member")
                || text.equals("attendee")
                || text.equals("role_attendee")
                || text.equals("user")
                || text.equals("role_user")
                || text.contains("tham du")
                || text.contains("nhan vien")
                || text.contains("participant");
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String stripped = Normalizer.normalize(input, Normalizer.Form.NFD);
        stripped = DIACRITICS.matcher(stripped).replaceAll("");
        return stripped.replace("đ", "d").replace("Đ", "d")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
