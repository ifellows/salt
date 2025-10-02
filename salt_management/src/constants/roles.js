// User roles and permissions constants

const ROLES = {
    ADMINISTRATOR: 'administrator',
    LAB_STAFF: 'lab_staff'
};

// Define permissions for each role
const PERMISSIONS = {
    [ROLES.ADMINISTRATOR]: {
        // Full system access
        canAccessAdmin: true,
        canManageUsers: true,
        canManageSurveys: true,
        canManageFacilities: true,
        canViewReports: true,
        canConfigureLabTests: true,
        canViewLabResults: true,
        canExportData: true,
        canAccessSystemSettings: true
    },
    [ROLES.LAB_STAFF]: {
        // Limited access for lab data entry
        canAccessAdmin: false,
        canManageUsers: false,
        canManageSurveys: false,
        canManageFacilities: false,
        canViewReports: false,
        canConfigureLabTests: false,
        canViewLabResults: false,  // Can only enter, not view
        canExportData: false,
        canAccessSystemSettings: false,
        canEnterLabResults: true   // Specific permission for lab staff
    }
};

// Helper functions for permission checking
const hasPermission = (userRole, permission) => {
    const rolePermissions = PERMISSIONS[userRole];
    if (!rolePermissions) {
        return false;
    }
    return rolePermissions[permission] === true;
};

const getRolePermissions = (userRole) => {
    return PERMISSIONS[userRole] || {};
};

const isValidRole = (role) => {
    return Object.values(ROLES).includes(role);
};

const getAllRoles = () => {
    return Object.values(ROLES);
};

const getRoleDisplayName = (role) => {
    switch(role) {
        case ROLES.ADMINISTRATOR:
            return 'Administrator';
        case ROLES.LAB_STAFF:
            return 'Laboratory Staff';
        default:
            return 'Unknown';
    }
};

module.exports = {
    ROLES,
    PERMISSIONS,
    hasPermission,
    getRolePermissions,
    isValidRole,
    getAllRoles,
    getRoleDisplayName
};