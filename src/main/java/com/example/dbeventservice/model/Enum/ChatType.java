package com.example.dbeventservice.model.Enum;

public enum ChatType {
    DIRECT,          // user ↔ user (owner or admin included)
    RESALE,          // buyer ↔ seller for a resale post
    PROMOTER_GROUP,  // group for event promoters
    ADMIN            // dedicated support/admin channel (optional)
}
