package com.endpointposture.job;

import com.endpointposture.endpoint.EndpointRepository;
import com.endpointposture.endpoint.dto.PageResponse;
import com.endpointposture.job.dto.JobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobPaginationTest {
    @Test
    void serviceBoundsSizeAndReturnsPaginationMetadata() {
        PostureJobRepository jobs = mock(PostureJobRepository.class);
        JobService service = new JobService(jobs, mock(EndpointRepository.class));
        when(jobs.findRecent(any())).thenReturn(List.of());
        when(jobs.count()).thenReturn(450L);

        PageResponse<PostureJob> result = service.pageRecent(-1, 5000);

        assertEquals(0, result.page());
        assertEquals(200, result.size());
        assertEquals(450, result.total());
        verify(jobs).findRecent(PageRequest.of(0, 200));
    }

    @Test
    void controllerExposesMetadataPageWithoutChangingLimitList() {
        JobService service = mock(JobService.class);
        JobController controller = new JobController(service);
        when(service.pageRecent(0, 200)).thenReturn(new PageResponse<>(List.of(), 525, 0, 200));

        PageResponse<JobResponse> result = controller.page(0, 1000);

        assertEquals(525, result.total());
        assertEquals(200, result.size());
        verify(service).pageRecent(0, 200);
    }
}
