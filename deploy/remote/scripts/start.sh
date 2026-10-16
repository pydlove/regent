#!/bin/bash
systemctl start regent
systemctl status regent --no-pager | head -5
