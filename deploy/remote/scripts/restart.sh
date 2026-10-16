#!/bin/bash
systemctl restart regent-relay
systemctl status regent-relay --no-pager | head -5
