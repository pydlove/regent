package com.pydlove.regent.review.interfaces.dto;

import com.pydlove.regent.review.domain.model.ReviewHunk;

/** 关键 diff hunk。 */
public record ReviewHunkResponse(
        String filePath,
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        String content) {

    public static ReviewHunkResponse from(ReviewHunk hunk) {
        return new ReviewHunkResponse(hunk.filePath(), hunk.oldStart(), hunk.oldLines(),
                hunk.newStart(), hunk.newLines(), hunk.content());
    }
}
