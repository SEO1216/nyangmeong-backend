package com.nyangmeong.notification.dto;

/**
 * [알림 모두 읽음 응답]
 * - count: 이번에 읽음 처리한 건수. 이미 0건이어도 0으로 성공
 */
public record NotificationReadAllResponse(int count) {
}
