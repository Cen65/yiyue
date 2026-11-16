package com.yiyue.model.resp;

import java.util.List;

public record CatalogResp(
    List<CampusResp> campuses, List<DepartmentResp> departments, List<DoctorResp> doctors) {}
