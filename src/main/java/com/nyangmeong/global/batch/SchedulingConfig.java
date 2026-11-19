package com.nyangmeong.global.batch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * [배치 스케줄러 설정]
 * - 배치 cron 예약(BatchScheduleRegistrar)과 케어 일정 메일 재시도 예약(TaskScheduler.schedule)이 같이 쓰는 스레드 풀
 */
@EnableScheduling
@Configuration
public class SchedulingConfig {

    /**
     * 스케줄러 스레드 풀
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
