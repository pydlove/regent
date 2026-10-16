#!/bin/bash
systemctl restart regent
systemctl status regent --no-pager | head -5
