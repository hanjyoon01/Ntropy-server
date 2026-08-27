package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.Job;

/**
 * 테스트용 인메모리 JobMapper 구현체. 여러 서비스 테스트에서 공용으로 사용한다.
 * seed()로 jobId를 직접 지정해 미리 데이터를 채워둘 수 있다.
 */
public class InMemoryJobMapper implements JobMapper {

    private final Map<Long, Job> store = new LinkedHashMap<>();
    private long sequence = 1;

    /** insert()를 거치지 않고 jobId를 지정해 바로 등록한다(다른 서비스 테스트 준비용). */
    public void seed(Job job) {
        store.put(job.getJobId(), job);
        if (job.getJobId() >= sequence) {
            sequence = job.getJobId() + 1;
        }
    }

    @Override
    public void insert(Job job) {
        job.setJobId(sequence++);
        store.put(job.getJobId(), job);
    }

    @Override
    public Job findById(Long jobId) {
        return store.get(jobId);
    }

    @Override
    public List<Job> findByUserId(Long userId) {
        List<Job> result = new ArrayList<>();
        for (Job job : store.values()) {
            if (userId.equals(job.getUserId())) {
                result.add(job);
            }
        }
        return result;
    }

    @Override
    public List<Job> findByUserIdIn(List<Long> userIds) {
        List<Job> result = new ArrayList<>();
        for (Job job : store.values()) {
            if (userIds.contains(job.getUserId())) {
                result.add(job);
            }
        }
        return result;
    }

    @Override
    public void update(Job job) {
        store.put(job.getJobId(), job);
    }

    @Override
    public void deleteById(Long jobId) {
        store.remove(jobId);
    }
}
