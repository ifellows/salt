const express = require('express');
const router = express.Router();
const { requireAdmin } = require('../../middleware/auth');
const { getAsync, allAsync } = require('../../models/database');
const { ROLES, getRoleDisplayName, getAllRoles } = require('../../constants/roles');

// User management main page
router.get('/admin/users', requireAdmin, async (req, res) => {
    try {
        // Get all users
        const users = await allAsync(`
            SELECT id, username, email, full_name, role, is_active,
                   created_at, last_login, updated_at
            FROM admin_users
            ORDER BY created_at DESC
        `);

        // Add role display names
        const enhancedUsers = users.map(user => ({
            ...user,
            roleDisplay: getRoleDisplayName(user.role)
        }));

        res.render('pages/userManagement', {
            title: 'User Management',
            users: enhancedUsers,
            roles: getAllRoles().map(role => ({
                value: role,
                display: getRoleDisplayName(role)
            })),
            user: req.user,
            success: req.query.success,
            error: req.query.error
        });
    } catch (error) {
        console.error('Error loading user management page:', error);
        res.status(500).render('pages/error', {
            title: 'Error',
            message: 'Failed to load user management page'
        });
    }
});

// Create/edit user form
router.get('/admin/users/new', requireAdmin, async (req, res) => {
    res.render('pages/userForm', {
        title: 'Create New User',
        user: req.user,
        editUser: null,
        roles: getAllRoles().map(role => ({
            value: role,
            display: getRoleDisplayName(role)
        })),
        error: req.query.error
    });
});

// Edit user form
router.get('/admin/users/:id/edit', requireAdmin, async (req, res) => {
    try {
        const userId = req.params.id;
        const editUser = await getAsync(
            'SELECT * FROM admin_users WHERE id = ?',
            [userId]
        );

        if (!editUser) {
            return res.redirect('/admin/users?error=' + encodeURIComponent('User not found'));
        }

        res.render('pages/userForm', {
            title: 'Edit User',
            user: req.user,
            editUser: editUser,
            roles: getAllRoles().map(role => ({
                value: role,
                display: getRoleDisplayName(role)
            })),
            error: req.query.error
        });
    } catch (error) {
        console.error('Error loading user edit form:', error);
        res.redirect('/admin/users?error=' + encodeURIComponent('Failed to load user'));
    }
});

module.exports = router;