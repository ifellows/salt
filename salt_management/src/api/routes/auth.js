const express = require('express');
const bcrypt = require('bcrypt');
const { body, validationResult } = require('express-validator');
const { getAsync, runAsync } = require('../../models/database');
const router = express.Router();

// Admin login
router.post('/login', [
    body('username').notEmpty().trim(),
    body('password').notEmpty()
], async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({ errors: errors.array() });
    }

    const { username, password } = req.body;

    try {
        // Get user from database
        const user = await getAsync(
            'SELECT * FROM admin_users WHERE username = ? AND is_active = 1',
            [username]
        );

        if (!user) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        // Verify password
        const isValid = await bcrypt.compare(password, user.password_hash);
        if (!isValid) {
            return res.status(401).json({ error: 'Invalid credentials' });
        }

        // Update last login
        await runAsync(
            'UPDATE admin_users SET last_login = CURRENT_TIMESTAMP WHERE id = ?',
            [user.id]
        );

        // Set session
        req.session.userId = user.id;
        req.session.username = user.username;

        res.json({
            message: 'Login successful',
            user: {
                id: user.id,
                username: user.username
            }
        });
    } catch (error) {
        console.error('Login error:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// Admin logout
router.post('/logout', (req, res) => {
    req.session.destroy((err) => {
        if (err) {
            return res.status(500).json({ error: 'Logout failed' });
        }
        res.json({ message: 'Logout successful' });
    });
});

// Check authentication status
router.get('/status', (req, res) => {
    if (req.session.userId) {
        res.json({
            authenticated: true,
            user: {
                id: req.session.userId,
                username: req.session.username
            }
        });
    } else {
        res.json({ authenticated: false });
    }
});

module.exports = router;