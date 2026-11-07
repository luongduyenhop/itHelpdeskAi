package com.aihelpdesk.user.service;

import com.aihelpdesk.user.dto.RegisterEmployeeRequest;
import com.aihelpdesk.user.dto.RegisterEmployeeResponse;

public interface UserService {

    RegisterEmployeeResponse registerEmployee(RegisterEmployeeRequest request);
}
