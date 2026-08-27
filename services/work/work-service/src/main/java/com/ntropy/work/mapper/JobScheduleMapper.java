package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.JobSchedule;

@Mapper
public interface JobScheduleMapper {

    void insert(JobSchedule jobSchedule);

    JobSchedule findById(Long scheduleId);

    List<JobSchedule> findByJobId(Long jobId);

    void update(JobSchedule jobSchedule);

    void deleteById(Long scheduleId);

    void deleteByJobId(Long jobId);
}
